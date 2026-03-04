class WebCTRLAPIClient {
  #url;
  #publicApiKey;
  #privateApiKey;
  #cryptoKey;
  #useApiKeyAuth;
  #dbid = null;
  retryCount = 3;
  retryDelay = 30000; // 30 seconds
  timeout = 30000; // 30 seconds
  constructor(baseApiServerUrl, publicApiKey, privateApiKey, resolveCurrentLocation=true) {
    if (baseApiServerUrl){
      baseApiServerUrl = baseApiServerUrl.replace(/\/+$/g, '').replace(/\/api$/gi, '').replace(/\/RestAPI$/gi, '');
    }else{
      baseApiServerUrl = window.location.origin;
    }
    this.#url = `${baseApiServerUrl}/RestAPI/api`;
    if (publicApiKey && privateApiKey){
      this.#publicApiKey = publicApiKey;
      this.#privateApiKey = privateApiKey;
      // Use ApiKey auth if HTTPS and both keys are provided
      this.#useApiKeyAuth = this.#url.startsWith('https://');
    }else{
      this.#publicApiKey = null;
      this.#privateApiKey = null;
      this.#useApiKeyAuth = false;
    }
    this.#cryptoKey = null;
    if (resolveCurrentLocation){
      // Attempt to determine the DBID of the session's current location
      try{
        const m = window.location.href.match(/\/~dbid\/(\d+)/);
        if (m){
          this.#dbid = Number(m[1]);
        }else if (window.top?.document){
          this.#dbid = Array.from(
            Array.from(window.top.document.querySelectorAll('IFRAME') ?? []).find(
              iframe => iframe.src.includes('/navpane/')
            )?.contentWindow?.document?.querySelectorAll('IFRAME') ?? []
          ).map(
            iframe => {
              if (iframe?.contentWindow?.getSelectedDbid && typeof iframe.contentWindow.getSelectedDbid === 'function'){
                try {
                  return Number(iframe.contentWindow.getSelectedDbid());
                }catch(e){
                  return null;
                }
              }else{
                return null;
              }
            }
          ).find(item => item!==null) ?? null;
        }
      }catch(e){
        console.error(e);
      }
    }
  }
  async sendRequest(endpoint, data, abortSignal=null) {
    endpoint = endpoint.replace(/\/+$/g, '').replace(/^\/+/g, '');
    if (!data){
      data = {};
    }
    if (this.#dbid!==null && !('contextDBID' in data)){
      data['contextDBID'] = this.#dbid;
    }
    let stat = 0, ret = null;
    for (let i=0;i<this.retryCount;++i){
      if (abortSignal?.aborted){
        break;
      }
      let response;
      try {
        if (stat==429){
          await WebCTRLAPIClient.#delay(this.retryDelay, abortSignal);
        }
        const { body, signature } = await this.#buildJWT(endpoint, data);
        let headers = {
          'Content-Type': 'application/json; charset=utf-8',
          'Accept': 'application/json'
        };
        if (this.#useApiKeyAuth){
          headers['Authorization'] = `ApiKey ${this.#publicApiKey}:${this.#privateApiKey}`;
        }else if (signature!==null){
          headers['Authorization'] = `Bearer ${signature}`;
        }
        const timeoutSignal = AbortSignal.timeout(this.timeout);
        const fetchSignal = abortSignal===null ? timeoutSignal : AbortSignal.any([abortSignal, timeoutSignal]);
        response = await fetch(`${this.#url}/${endpoint}`, {
          method: 'POST',
          headers: headers,
          body: body,
          signal: fetchSignal
        });
        ret = JSON.parse(await response.text(), (_k,v,ctx) => {
          if (typeof v==='number' && typeof ctx.source==='string' && /^-?\d+$/.test(ctx.source) && !Number.isSafeInteger(v)){
            return BigInt(ctx.source);
          }else{
            return v;
          }
        });
      } catch (error) {
        console.error(error);
        ret = null;
        if (abortSignal?.aborted){
          stat = 0;
          break;
        }
      }
      stat = (response?.status)??0;
      if (stat!=409 && stat!=429){
        break;
      }
    }
    return {
      status: stat,
      response: ret
    };
  }
  async #buildJWT(endpoint, data) {
    let hasBigInts = false;
    let body = JSON.stringify(
      this.#publicApiKey===null || this.#useApiKeyAuth ? data : {
        iss: this.#publicApiKey,
        aud: endpoint,
        jti: crypto.randomUUID(),
        iat: Math.floor(Date.now() / 1000),
        data: data
      }, (_k,v)=>{
        if (typeof v==='bigint'){
          hasBigInts = true;
          return 'BIGINT:'+String(v);
        }else{
          return v;
        }
      }
    );
    if (hasBigInts){
      body = body.replaceAll(/"BIGINT:(-?\d+)"/g, '$1');
    }
    const signature = await this.#signWithHMACSHA256(body);
    return {
      body: body,
      signature: signature
    };
  }
  async #signWithHMACSHA256(data) {
    if (this.#privateApiKey===null || this.#useApiKeyAuth){
      return null;
    }
    const enc = new TextEncoder();
    if (this.#cryptoKey===null){
      const keyData = enc.encode(this.#privateApiKey);
      this.#cryptoKey = await crypto.subtle.importKey('raw', keyData, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
    }
    const signature = await crypto.subtle.sign('HMAC', this.#cryptoKey, enc.encode(data));
    return WebCTRLAPIClient.#base64URLEncode(String.fromCharCode(...new Uint8Array(signature)));
  }
  static #base64URLEncode(str) {
    return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
  static #delay(ms, signal=null) {
    return new Promise((resolve, reject) => {
      const cleanup = () => {
        signal?.removeEventListener('abort', onAbort);
      };
      const id = setTimeout(() => {
        cleanup();
        resolve();
      }, ms);
      const onAbort = () => {
        clearTimeout(id);
        cleanup();
        reject(new DOMException('The operation was aborted.', 'AbortError'));
      };
      signal?.addEventListener('abort', onAbort, { once: true });
      if (signal?.aborted){
        onAbort();
        return;
      }
    });
  }
}
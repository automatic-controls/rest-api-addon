
# REST API Add-On

---
> **WebCTRL** is a trademark of Automated Logic Corporation. Any other trademarks mentioned herein are the property of their respective owners.
---

## Overview

The REST API Add-On provides secure access to WebCTRL server data and functionality via HTTP endpoints. It is designed for integration with custom clients, automation scripts, and web applications.

---
> :book: **Full interactive documentation** is available from the add-on's main page once installed on your WebCTRL server. This README is meant only to give a brief overview of the capabilities.
---

## :satellite: Endpoints

The add-on exposes the following endpoints:

- `GetSchema` — Retrieves the JSON schema used to validate input for an API endpoint.
- `ResolveGQL` — Resolves a GQL path or DBID and retrieves details about the node.
- `ExecCommand` — Executes manual commands on the server.

---

## :hammer_and_wrench: SDKs

- **JavaScript SDK**: For use in HTML content controls within graphics and other web pages. Provides convenient methods for sending API requests and handling authentication automatically.
- **PowerShell SDK**: For scripted automation and CLI use cases. Compatible with PowerShell 5.1 and later.

---

## :package: Third-Party Libraries

This add-on uses the following third-party libraries:

- [fastjson2](https://github.com/alibaba/fastjson2) v2.0.58 — High-performance JSON parser and serializer for Java.
- [fontawesome](https://fontawesome.com/) v6.7.2 — Icon toolkit for scalable vector icons.
- [highlight.js](https://highlightjs.org/) v11.11.1 — Syntax highlighting for code blocks in documentation.
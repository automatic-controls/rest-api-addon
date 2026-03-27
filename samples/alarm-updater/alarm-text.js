
// Fill out these parameters
const rootLocation = "/trees/geographic";
const alarmTemplate = "universal";
const alarmText = "$source$: $source:description$ is active at $location_path$.";
const returnText = "$source$: $source:description$ has returned to normal at $location_path$.";

// No need to change anything below this line
const batchSize = 250;
const alarmTemplatePath = "#eventtemplates/bacnet/" + alarmTemplate;
const client = new WebCTRLAPIClient();

(async () => {
  try {
    const updateErrors = [];
    let shouldSetAlarmTemplate = false;
    const addApiErrors = function (label, errors) {
      const erroredDbids = {};
      if (!errors || errors.length === 0) {
        return 0;
      }
      console.warn(label, errors);
      for (let i = 0; i < errors.length; i++) {
        const err = errors[i];
        if (typeof err === "string") {
          updateErrors.push(err);
        } else if (err && typeof err === "object") {
          if (err.dbid !== undefined && err.dbid !== null) {
            erroredDbids[String(err.dbid)] = true;
          }
          if (err.message || err.error) {
            updateErrors.push(err.message || err.error);
          } else {
            try {
              updateErrors.push(JSON.stringify(err));
            } catch (stringifyError) {
              updateErrors.push("Unserializable error object");
            }
          }
        } else {
          updateErrors.push(String(err));
        }
      }
      return Object.keys(erroredDbids).length;
    };

    if (alarmTemplate && String(alarmTemplate).trim() !== "") {
      let resolveResult;
      try {
        resolveResult = await client.sendRequest("ResolveGQL", {
          "path": alarmTemplatePath
        });
      } catch (resolveError) {
        console.warn("ResolveGQL request failed for alarm template path:", alarmTemplatePath, resolveError);
      }

      if (resolveResult?.status === 200) {
        shouldSetAlarmTemplate = true;
      } else {
        if (resolveResult !== undefined) {
          console.warn("ResolveGQL response:", resolveResult);
        }
        console.warn("Alarm template path was not found and will be skipped:", alarmTemplatePath);
      }
    } else {
      console.warn("Alarm template is empty. Template update will be skipped.");
    }

    let searchResult = await client.sendRequest("SearchGQL", {
      "steps": [
        {
          "includeRoots": true,
          "intermediateFilter": {
            "hasType": [
              "TREE",
              "AREA"
            ]
          },
          "leafFilter": {
            "hasType": [
              "BEQU"
            ]
          }
        },
        {
          "intermediateFilter": {
            "hasType": [
              "BAF"
            ]
          },
          "leafFilter": {
            "and": [
              {
                "hasGQL": "~event_alarm_text"
              },
              {
                "hasGQL": "~event_return_text"
              },
              {
                "hasGQL": "~event_template"
              }
            ]
          }
        }
      ],
      "roots": [
        rootLocation
      ]
    });

    if (searchResult?.status !== 200) {
      console.error("SearchGQL response:", searchResult);
      console.log("Failed to search for matching nodes.");
      return;
    }

    addApiErrors("SearchGQL returned errors:", searchResult.response?.errors);

    let dbids = searchResult.response?.dbids || [];
    if (dbids.length === 0) {
      const fallbackResult = await client.sendRequest("SearchGQL", {
        "steps": [
          {
            "includeRoots": true,
            "intermediateFilter": {
              "hasType": [
                "TREE",
                "SITE",
                "BNET",
                "BHWRD",
                "BHWD"
              ]
            },
            "leafFilter": {
              "hasType": [
                "LINK"
              ]
            },
            "jump": "~target"
          },
          {
            "intermediateFilter": {
              "hasType": [
                "BAF"
              ]
            },
            "leafFilter": {
              "and": [
                {
                  "hasGQL": "~event_alarm_text"
                },
                {
                  "hasGQL": "~event_return_text"
                },
                {
                  "hasGQL": "~event_template"
                }
              ]
            }
          }
        ],
        "roots": [
          rootLocation
        ]
      });

      if (fallbackResult?.status !== 200) {
        console.error("SearchGQL fallback response:", fallbackResult);
        console.log("Failed to search for matching nodes (network tree fallback).");
        return;
      }

      addApiErrors("SearchGQL fallback returned errors:", fallbackResult.response?.errors);
      dbids = fallbackResult.response?.dbids || [];
    }

    if (dbids.length === 0) {
      console.log("No matching nodes were found.");
      return;
    }

    const setExpressions = [
      {
        "expression": "~event_alarm_text.value",
        "value": alarmText
      },
      {
        "expression": "~event_return_text.value",
        "value": returnText
      }
    ];
    if (shouldSetAlarmTemplate) {
      setExpressions.unshift({
        "expression": "~event_template.value",
        "value": alarmTemplate
      });
    }
    let successCount = 0;
    for (let i = 0; i < dbids.length; i += batchSize) {
      const batch = dbids.slice(i, i + batchSize);
      const execResult = await client.sendRequest("ExecGQL", {
        "nodes": batch,
        "set": setExpressions,
        "fieldAccess": true
      });

      if (execResult?.status !== 200) {
        console.error("ExecGQL response:", execResult);
        updateErrors.push("ExecGQL failed with status " + (execResult?.status || "unknown"));
        continue;
      }

      const failedDbidCount = addApiErrors("ExecGQL returned errors:", execResult.response?.errors);
      const appliedCount = batch.length - failedDbidCount;
      successCount += appliedCount > 0 ? appliedCount : 0;
    }

    if (updateErrors.length > 0) {
      console.log(
        "Update completed for " + successCount + " node(s) with " + updateErrors.length +
        " error(s). Check console for details."
      );
    } else {
      if (shouldSetAlarmTemplate) {
        console.log("Updated alarm text, return text, and template for " + successCount + " node(s).");
      } else {
        console.log("Updated alarm and return text for " + successCount + " node(s). Template update was skipped.");
      }
    }
  } catch (error) {
    console.error("Alarm update error:", error);
    console.log("An error occurred while updating alarms: " + (error?.message || error));
  }
})();
# Contract baseline

`OpenApiContractIT` compares the OpenAPI document served at `/openapi.json` with `openapi.json` here.

- No `openapi.json` yet? The test is skipped. Create the baseline once and commit it:
  `./scripts/update-contract.sh`
- Test failing after a change? The public API changed. If the change is intended **and backward
  compatible** (new optional fields, new query parameters, new endpoints), rerun the script and
  commit the new baseline with the code. Removing or renaming fields, making something required,
  or changing types breaks clients: publish it as `/api/v2` instead.

# Maestro UI Tests

YAML-based UI flows using element IDs sourced from `src/test/resources/testdata/elements/`.

## Structure

```
maestro/
  flows/
    _login.yaml          # reusable login subflow
    login.yaml           # login smoke test
    home.yaml            # home screen navigation smoke
```

## Run

```bash
# Single flow
maestro test maestro/flows/login.yaml --env USERNAME=9999999999 --env OTP=1234

# All smoke tests
maestro test maestro/flows --include-tags smoke --env USERNAME=9999999999 --env OTP=1234
```

## Element IDs

All `id` values map to `accessibilityId` from Forge element YAMLs:
- `src/test/resources/testdata/elements/login.yaml`
- `src/test/resources/testdata/elements/home.yaml`
- `src/test/resources/testdata/elements/common.yaml`

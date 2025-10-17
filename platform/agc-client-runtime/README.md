## Generate Key::
```shell
cat <<'JSON' \
| openssl enc -aes-256-cbc -pbkdf2 -iter 100000 -salt -pass pass: \
| base64 | tr -d '\n' ; echo
{
  "namespace": "<namespace_value>",
  "target": "<target_value>",
  "apiKey":"<api_key_value>"
}
JSON
```

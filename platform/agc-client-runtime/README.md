## Generate Key::
```shell 
  cat <<'JSON' \
| openssl enc -aes-256-cbc -pbkdf2 -iter 100000 -salt -pass pass: \
| base64 | tr -d '\n' ; echo
{
  "namespace": <namespace>,
  "target": <target>,
  "apiKey":<apiKey>
}
JSON
```

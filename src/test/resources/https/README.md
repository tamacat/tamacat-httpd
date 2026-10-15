

keytool -import -alias localhost -file localhost.crt -keystore cacerts

changeit

keytool -delete -alias localhost -keystore cacerts


certmgr


## Regenerating the mutual-TLS test PKI (client-cert/)

The files under `client-cert/` form one test PKI used by the mutual-TLS
tests (`org.tamacat.httpd.core.HttpEngineMutualTlsTest`) and by
`client-cert/server.properties`:

| File | Content |
|---|---|
| `ca/tamacat.org_CA_Root_X1.crt` | Root CA, `O=tamacat.org, CN=tamacat.org CA Root X1` (PEM) |
| `ca/tamacat.org_CA_intermediate_G2.crt` | Intermediate CA, `O=tamacat.org, CN=tamacat.org CA intermediate G2` (PEM) |
| `localhost.p12` | Server key store, alias `localhost`, `CN=localhost`, SAN `DNS:localhost, IP:127.0.0.1`, chain of 3 |
| `test-clients/test01@example.com.p12` | Client key store, alias `test01@example.com`, `O=tamacat.org, emailAddress=test01@example.com`, chain of 3 |
| `CRL.der` | CRL issued by the intermediate CA, revokes nothing |
| `cacerts` | JKS trust store with aliases `tamacat.org ca root x1` and `tamacat.org ca intermediate g2 (tamacat.org ca root x1)` |

All key stores use the password given by `https.keyPassword` in
`client-cert/server.properties` (the key password equals the store password).
The set was last regenerated on 2026-10-15 (RSA 4096 CAs, RSA 2048 end
entities, SHA-256, valid for 30 years) because the previous client
certificate expired on 2025-01-29. The CA private keys are NOT kept in the
repository; to issue a new certificate, regenerate the whole set.

Run the following with OpenSSL 3.x and JDK `keytool` (tested with OpenSSL
3.2.4 and JDK 25) as `STOREPASS=<store password> sh gen.sh <work dir> <output dir>`,
then copy the contents of `<output dir>` into `client-cert/` and delete
`<work dir>` (it holds the CA private keys). On Windows, give both
directories as `C:/...` paths and save the PEM files with LF line endings.

```sh
#!/bin/sh
set -eu
export MSYS_NO_PATHCONV=1   # Git Bash only: keep "/O=..." subjects as-is
W="$1"; O="$2"
: "${STOREPASS:?set STOREPASS}"
export STOREPASS
DAYS=10957   # 30 years
mkdir -p "$W" "$O/ca" "$O/test-clients"
cd "$W"

cat > ext.cnf <<'EOF'
[req]
distinguished_name = dn
[dn]
[root]
basicConstraints = critical, CA:TRUE
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
[intermediate]
basicConstraints = critical, CA:TRUE, pathlen:0
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
[server]
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = DNS:localhost, IP:127.0.0.1
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
[client]
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = clientAuth, emailProtection
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
EOF

# 1. Root CA (self-signed)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out root.key
openssl req -new -x509 -sha256 -days $DAYS -key root.key \
  -subj "/O=tamacat.org/CN=tamacat.org CA Root X1" \
  -config ext.cnf -extensions root -set_serial 0x$(openssl rand -hex 8) -out root.crt

# 2. Intermediate CA (signed by the root)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out intermediate.key
openssl req -new -key intermediate.key -subj "/O=tamacat.org/CN=tamacat.org CA intermediate G2" -out intermediate.csr
openssl x509 -req -sha256 -days $DAYS -in intermediate.csr -CA root.crt -CAkey root.key \
  -set_serial 0x$(openssl rand -hex 8) -extfile ext.cnf -extensions intermediate -out intermediate.crt

# 3. Server certificate CN=localhost (signed by the intermediate)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out localhost.key
openssl req -new -key localhost.key -subj "/O=tamacat.org/CN=localhost" -out localhost.csr
openssl x509 -req -sha256 -days $DAYS -in localhost.csr -CA intermediate.crt -CAkey intermediate.key \
  -set_serial 0x$(openssl rand -hex 8) -extfile ext.cnf -extensions server -out localhost.crt

# 4. Client certificate emailAddress=test01@example.com (signed by the intermediate)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out client.key
openssl req -new -key client.key -subj "/O=tamacat.org/emailAddress=test01@example.com" -out client.csr
openssl x509 -req -sha256 -days $DAYS -in client.csr -CA intermediate.crt -CAkey intermediate.key \
  -set_serial 0x$(openssl rand -hex 8) -extfile ext.cnf -extensions client -out client.crt

# 5. Empty CRL issued by the intermediate (the previous CRL revoked nothing)
mkdir -p crldb; : > crldb/index.txt; echo 01 > crldb/crlnumber
cat > ca.cnf <<'EOF'
[ca]
default_ca = intermediate
[intermediate]
database = crldb/index.txt
crlnumber = crldb/crlnumber
default_md = sha256
crl_extensions = crl_ext
[crl_ext]
authorityKeyIdentifier = keyid:always
EOF
openssl ca -config ca.cnf -gencrl -keyfile intermediate.key -cert intermediate.crt \
  -crldays $DAYS -out crl.pem
openssl crl -in crl.pem -outform DER -out "$O/CRL.der"

# 6. Key stores (PKCS#12, OpenSSL 3 defaults: AES-256-CBC, PBKDF2, HMAC-SHA256)
cat intermediate.crt root.crt > chain.pem
openssl pkcs12 -export -name localhost -inkey localhost.key -in localhost.crt \
  -certfile chain.pem -passout env:STOREPASS -out "$O/localhost.p12"
openssl pkcs12 -export -name test01@example.com -inkey client.key -in client.crt \
  -certfile chain.pem -passout env:STOREPASS -out "$O/test-clients/test01@example.com.p12"

# 7. Trust store (JKS) with the same aliases as before, and the PEM CA certificates
rm -f "$O/cacerts"
keytool -importcert -noprompt -storetype JKS -keystore "$O/cacerts" -storepass:env STOREPASS \
  -alias "tamacat.org ca root x1" -file root.crt
keytool -importcert -noprompt -storetype JKS -keystore "$O/cacerts" -storepass:env STOREPASS \
  -alias "tamacat.org ca intermediate g2 (tamacat.org ca root x1)" -file intermediate.crt
openssl x509 -in root.crt -out "$O/ca/tamacat.org_CA_Root_X1.crt"
openssl x509 -in intermediate.crt -out "$O/ca/tamacat.org_CA_intermediate_G2.crt"
```

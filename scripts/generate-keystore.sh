#!/usr/bin/env bash
# Generates a self-signed keystore for PDF PAdES digital signatures.
# Run this ONCE before first docker compose up.
# The certificate is embedded in signed PDFs for long-term validation.

set -e

KEYSTORE_DIR="./keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/ssv-signing.p12"
ALIAS="ssv-pdf-signer"

mkdir -p "$KEYSTORE_DIR"

if [ -f "$KEYSTORE_FILE" ]; then
  echo "Keystore already exists at $KEYSTORE_FILE. Delete it first to regenerate."
  exit 0
fi

# Load password from .env if present
if [ -f ".env" ]; then
  source .env
fi

PASS="${SIGNING_KEYSTORE_PASSWORD:-changeit}"

echo "Generating RSA-2048 signing key..."
keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -storetype PKCS12 \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$PASS" \
  -keypass "$PASS" \
  -dname "CN=SSV DMS Signing Authority, OU=Engineering, O=Telecom, L=Colombo, ST=Western, C=LK" \
  -ext "KeyUsage=digitalSignature,nonRepudiation" \
  -ext "ExtendedKeyUsage=1.2.840.113583.1.1.5"   # Adobe PDF signing OID

echo ""
echo "✓ Keystore created at $KEYSTORE_FILE"
echo "  Alias:    $ALIAS"
echo "  Validity: 10 years"
echo ""
echo "Export public certificate (for distribution/verification):"
echo "  keytool -exportcert -alias $ALIAS -keystore $KEYSTORE_FILE -storepass \$PASS -file ssv-signer.cer -rfc"

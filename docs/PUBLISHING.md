# Publishing Codex UI

JetBrains requires the first Marketplace publication to be uploaded manually.
After the first version is approved, tagged releases can publish automatically.

## One-time setup

1. Sign in to [JetBrains Marketplace](https://plugins.jetbrains.com/author/me).
2. Create a Marketplace token under
   [My Tokens](https://plugins.jetbrains.com/author/me/tokens).
3. Generate a signing key and certificate outside the repository:

   ```bash
   openssl genpkey \
     -aes-256-cbc \
     -algorithm RSA \
     -out private_encrypted.pem \
     -pkeyopt rsa_keygen_bits:4096
   openssl req \
     -key private_encrypted.pem \
     -new \
     -x509 \
     -days 3650 \
     -out chain.crt
   ```

4. Create a protected GitHub Environment named `marketplace-release`.
   Require the repository owner as a reviewer and restrict deployment to tags
   matching `v*`.

5. Add these GitHub Actions environment secrets:

   - `CERTIFICATE_CHAIN`: contents of `chain.crt`
   - `PRIVATE_KEY`: contents of `private_encrypted.pem`
   - `PRIVATE_KEY_PASSWORD`: the password used while generating the key
   - `PUBLISH_TOKEN`: the JetBrains Marketplace token

   Never commit the certificate, private key, password, or token.

Release tags must point to commits already merged into `main`. The workflow
also waits for approval from the protected environment before it can read the
signing credentials or publish anything.

## First publication

1. Keep the repository variable `JETBRAINS_MARKETPLACE_ENABLED` unset.
2. Push the release commit and matching version tag:

   ```bash
   git push origin main
   git tag v1.1.18
   git push origin v1.1.18
   ```

3. Wait for the `Release` GitHub Actions workflow to complete.
4. Download the signed ZIP from the generated
   [GitHub Release](https://github.com/ichaly/codex-launcher/releases).
5. On the [Marketplace author page](https://plugins.jetbrains.com/author/me),
   choose **Add new plugin** and upload the signed ZIP.
6. Submit the independent plugin ID `com.github.ichaly.codex-ui` for review.

## Later publications

After the first Marketplace version is approved:

1. Set the GitHub Actions repository variable
   `JETBRAINS_MARKETPLACE_ENABLED` to `true`.
2. Bump `version` and change notes in `build.gradle.kts`.
3. Push a matching `v<version>` tag.

The release workflow will test and sign the plugin, publish it to Marketplace,
and attach the signed ZIP to the GitHub Release.

See the official JetBrains documentation for
[publishing](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)
and [plugin signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).

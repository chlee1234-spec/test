import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/**
 * Sign (v1+v2+v3) and verify an APK using Google's apksig library.
 * usage: SignApk sign   <keystore> <storepass> <alias> <in.apk> <out.apk> <minSdk>
 *        SignApk verify <apk>
 */
public class SignApk {
    public static void main(String[] a) throws Exception {
        if (a.length >= 2 && a[0].equals("verify")) {
            ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[1])).build().verify();
            System.out.println("verified=" + r.isVerified()
                    + " v1=" + r.isVerifiedUsingV1Scheme()
                    + " v2=" + r.isVerifiedUsingV2Scheme()
                    + " v3=" + r.isVerifiedUsingV3Scheme());
            for (ApkVerifier.IssueWithParams e : r.getErrors()) System.out.println("ERROR " + e);
            for (ApkVerifier.IssueWithParams w : r.getWarnings()) System.out.println("WARN  " + w);
            System.exit(r.isVerified() ? 0 : 1);
        }
        if (a.length == 7 && a[0].equals("sign")) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream in = new FileInputStream(a[1])) {
                ks.load(in, a[2].toCharArray());
            }
            PrivateKey key = (PrivateKey) ks.getKey(a[3], a[2].toCharArray());
            X509Certificate cert = (X509Certificate) ks.getCertificate(a[3]);
            ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder(
                    "petforge", key, Collections.singletonList(cert)).build();
            new ApkSigner.Builder(Collections.singletonList(cfg))
                    .setInputApk(new File(a[4]))
                    .setOutputApk(new File(a[5]))
                    .setMinSdkVersion(Integer.parseInt(a[6]))
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .build()
                    .sign();
            System.out.println("signed " + a[5]);
            return;
        }
        System.err.println("bad args");
        System.exit(2);
    }
}

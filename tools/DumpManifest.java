import com.android.apksig.internal.apk.AndroidBinXmlParser;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Dump an APK's binary AndroidManifest.xml using Google's AXML parser (from apksig). */
public class DumpManifest {
    public static void main(String[] a) throws Exception {
        byte[] data;
        try (ZipFile z = new ZipFile(a[0])) {
            ZipEntry e = z.getEntry("AndroidManifest.xml");
            try (InputStream in = z.getInputStream(e)) {
                data = in.readAllBytes();
            }
        }
        AndroidBinXmlParser p = new AndroidBinXmlParser(ByteBuffer.wrap(data));
        int depth = 0;
        int ev = p.getEventType();
        while (ev != AndroidBinXmlParser.EVENT_END_DOCUMENT) {
            if (ev == AndroidBinXmlParser.EVENT_START_ELEMENT) {
                StringBuilder sb = new StringBuilder("  ".repeat(depth) + "<" + p.getName());
                for (int i = 0; i < p.getAttributeCount(); i++) {
                    String val;
                    switch (p.getAttributeValueType(i)) {
                        case AndroidBinXmlParser.VALUE_TYPE_STRING: val = "\"" + p.getAttributeStringValue(i) + "\""; break;
                        case AndroidBinXmlParser.VALUE_TYPE_INT: val = "int " + p.getAttributeIntValue(i); break;
                        case AndroidBinXmlParser.VALUE_TYPE_BOOLEAN: val = "bool " + p.getAttributeBooleanValue(i); break;
                        default: val = "type" + p.getAttributeValueType(i);
                    }
                    sb.append(String.format(" %s[0x%08x]=%s", p.getAttributeName(i),
                            p.getAttributeNameResourceId(i), val));
                }
                System.out.println(sb + ">");
                depth++;
            } else if (ev == AndroidBinXmlParser.EVENT_END_ELEMENT) {
                depth--;
            }
            ev = p.next();
        }
    }
}

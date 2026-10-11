import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import javax.imageio.ImageIO;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

/** Decode the actual Qt screenshot using the same ZXing version and hints as Android. */
class QrDecodeTest {
    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(Path.of(args[0]).toFile());
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        var hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        String actual = new QRCodeReader().decode(bitmap, hints).getText();
        if (!actual.equals(Files.readString(Path.of(args[1]))))
            throw new AssertionError("Rendered QR does not match the active pairing payload");
        System.out.println("Rendered pairing QR decoded successfully with Android ZXing");
    }
}

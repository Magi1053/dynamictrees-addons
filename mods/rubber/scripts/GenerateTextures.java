import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Replaces the former Pillow scripts: retint wood + copy/recolor latex item icons. */
public class GenerateTextures {
    private static Path blockDir;
    private static Path itemDir;

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Path.of(args[0]).toAbsolutePath() : Path.of("").toAbsolutePath();
        blockDir = root.resolve("bundled_assets/textures/block");
        itemDir = root.resolve("bundled_assets/textures/item");
        ImageIO.setUseCache(false);
        generateWood();
        generateLatexItems();
    }

    private static void generateWood() throws Exception {
        Files.createDirectories(blockDir);
        fetchIfMissing(
            blockDir.resolve("_src_birch_planks.png"),
            "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21.1/assets/minecraft/textures/block/birch_planks.png");
        fetchIfMissing(
            blockDir.resolve("_src_stripped_jungle_log.png"),
            "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21.1/assets/minecraft/textures/block/stripped_jungle_log.png");

        Path top = blockDir.resolve("rubber_log_top.png");
        Path log = blockDir.resolve("rubber_log.png");
        if (!Files.isRegularFile(top)) {
            throw new IllegalStateException("Missing " + top);
        }
        if (!Files.isRegularFile(log)) {
            throw new IllegalStateException("Missing bundled source " + log);
        }

        tintImage(blockDir.resolve("_src_stripped_jungle_log.png"), blockDir.resolve("rubber_stripped_log.png"), top, 0.55);
        tintImage(blockDir.resolve("_src_birch_planks.png"), blockDir.resolve("rubber_planks.png"), top, 0.35);
        ImageIO.write(readRgba(top), "png", blockDir.resolve("rubber_log_top_thick.png").toFile());
    }

    private static void generateLatexItems() throws Exception {
        Files.createDirectories(itemDir);
        copy(itemDir.resolve("_src_raw_latex.png"), itemDir.resolve("raw_latex.png"));
        copy(itemDir.resolve("_src_coagulated_latex.png"), itemDir.resolve("coagulated_latex.png"));

        Path ballSrc = itemDir.resolve("_src_rubber_ball.png");
        Path ballOut = itemDir.resolve("rubber_ball.png");
        if (Files.isRegularFile(ballSrc)) {
            copy(ballSrc, ballOut);
            return;
        }
        BufferedImage image = readRgba(itemDir.resolve("_src_coagulated_latex.png"));
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                if (alpha == 0) {
                    continue;
                }
                int red = (argb >>> 16) & 0xff;
                int green = (argb >>> 8) & 0xff;
                int blue = argb & 0xff;
                int gray = (int) (red * 0.299 + green * 0.587 + blue * 0.114);
                int dark = Math.max(18, (int) (gray * 0.42));
                image.setRGB(x, y, (alpha << 24) | (dark << 16) | (dark << 8) | Math.min(255, dark + 8));
            }
        }
        ImageIO.write(image, "png", ballOut.toFile());
    }

    private static void fetchIfMissing(Path dest, String url) throws Exception {
        if (Files.isRegularFile(dest)) {
            return;
        }
        try (InputStream in = URI.create(url).toURL().openStream()) {
            Files.copy(in, dest);
        }
        System.out.println("Fetched " + dest.getFileName());
    }

    private static void copy(Path from, Path to) throws Exception {
        if (!Files.isRegularFile(from)) {
            throw new IllegalStateException("Missing source texture: " + from);
        }
        Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static BufferedImage readRgba(Path path) throws Exception {
        BufferedImage src = ImageIO.read(path.toFile());
        if (src == null) {
            throw new IllegalStateException("Unreadable image: " + path);
        }
        BufferedImage rgba = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics graphics = rgba.getGraphics();
        graphics.drawImage(src, 0, 0, null);
        graphics.dispose();
        return rgba;
    }

    private static double[] avgRgb(BufferedImage image) {
        long r = 0;
        long g = 0;
        long b = 0;
        int n = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                if (((argb >>> 24) & 0xff) == 0) {
                    continue;
                }
                r += (argb >>> 16) & 0xff;
                g += (argb >>> 8) & 0xff;
                b += argb & 0xff;
                n++;
            }
        }
        if (n == 0) {
            return new double[] {128, 128, 128};
        }
        return new double[] {r / (double) n, g / (double) n, b / (double) n};
    }

    private static void tintImage(Path source, Path target, Path reference, double greenPull) throws Exception {
        BufferedImage src = readRgba(source);
        BufferedImage ref = readRgba(reference);
        double[] srcAvg = avgRgb(src);
        double[] dstAvg = avgRgb(ref);
        double sr = Math.max(1, srcAvg[0]);
        double sg = Math.max(1, srcAvg[1]);
        double sb = Math.max(1, srcAvg[2]);
        double tr = dstAvg[0];
        double tg = dstAvg[1];
        double tb = dstAvg[2];
        int targetG = (int) ((tr + tb) / 2);

        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = src.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                if (alpha == 0) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                int r = (argb >>> 16) & 0xff;
                int g = (argb >>> 8) & 0xff;
                int b = argb & 0xff;
                int nr = clamp(r * (tr / sr));
                int ng = clamp(g * (tg / sg));
                int nb = clamp(b * (tb / sb));
                ng = (int) (ng * (1.0 - greenPull) + targetG * greenPull);
                out.setRGB(x, y, (alpha << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        ImageIO.write(out, "png", target.toFile());
        System.out.printf(
            "Wrote %s (src %.1f,%.1f,%.1f -> ref %.1f,%.1f,%.1f, green_pull=%s)%n",
            target.getFileName(),
            srcAvg[0],
            srcAvg[1],
            srcAvg[2],
            dstAvg[0],
            dstAvg[1],
            dstAvg[2],
            greenPull);
    }

    private static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, value));
    }
}

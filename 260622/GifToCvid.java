import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.w3c.dom.NodeList;

/**
 * 애니메이션 GIF -> CVID 포맷 변환기 (순수 Java, 외부 도구 불필요).
 *
 * 실행:
 *   java GifToCvid.java "입력.gif"                 -> 같은 이름의 .cvid 생성
 *   java GifToCvid.java "입력.gif" "출력.cvid"
 * 재생:
 *   java Player.java 출력.cvid
 *
 * ── 동작 원리 ──────────────────────────────────────────────────────
 *  · GIF는 javax.imageio 가 기본으로 디코딩한다(ImageReader). 단, GIF 프레임은
 *    보통 "바뀐 영역만" 담고 위치 오프셋과 폐기(disposal) 규칙을 가진다.
 *    그래서 논리화면(logical screen) 크기의 캔버스에 프레임을 차례로 합성해야
 *    올바른 전체 프레임이 만들어진다.
 *  · 폐기 규칙:
 *      none / doNotDispose      -> 그대로 둠 (다음 프레임이 위에 덧그려짐)
 *      restoreToBackgroundColor -> 그 영역을 비움(투명)
 *      restoreToPrevious        -> 이 프레임 그리기 전 상태로 되돌림
 *  · 완성된 각 프레임을 목표 크기로 축소 -> 픽셀의 밝기(0~255)를 0~99로 변환.
 *  · 출력 크기: 원본 비율 유지, 가로*세로(픽셀 수) <= MAX_PIXELS(5000).
 *
 *  ※ 투명 픽셀은 검은 배경 위에 올린 것으로 본다(어두움=0).
 *  ※ 플레이어는 100ms(10fps) 고정이라, GIF 원래 속도와 다를 수 있다(프레임 1:1 변환).
 *  ※ 터미널 글자는 세로로 길쭉해서, 원본 비율 그대로면 세로로 늘어나 보인다.
 *    보기 좋게 하려면 CHAR_ASPECT 를 2.0 으로.
 */
public class GifToCvid {

    static final int    MAX_PIXELS  = 5000; // 가로*세로 상한
    static final double CHAR_ASPECT = 1.0;  // 1.0=원본 비율, 2.0=글자비율 보정

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("사용법: java GifToCvid.java \"입력.gif\" [\"출력.cvid\"]");
            return;
        }
        Path inPath = Path.of(args[0]);
        if (!Files.exists(inPath)) {
            System.out.println("입력 파일을 찾을 수 없습니다: " + args[0]);
            return;
        }
        String output = (args.length >= 2) ? args[1] : replaceExt(args[0], ".cvid");

        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(inPath.toFile())) {
            reader.setInput(iis, false);
            int frames = reader.getNumImages(true);
            if (frames <= 0) { System.out.println("프레임이 없습니다."); return; }

            // ── 논리화면(캔버스) 크기 결정 ────────────────────────────
            int[] screen = canvasSize(reader, frames);
            int cw = screen[0], ch = screen[1];
            System.out.println("원본(논리화면): " + cw + "x" + ch + ", 프레임 " + frames + "개");

            // ── 비율 유지하며 가로*세로 <= MAX_PIXELS 로 목표 크기 계산 ──
            double effCh = ch / CHAR_ASPECT;
            double s = Math.sqrt(MAX_PIXELS / ((double) cw * effCh));
            int w = Math.max(1, (int) Math.floor(cw   * s));
            int h = Math.max(1, (int) Math.floor(effCh * s));
            while (w * h > MAX_PIXELS) { if (w >= h) w--; else h--; }
            System.out.println("출력 크기     : " + w + "*" + h + " (" + (w * h) + " 픽셀, 상한 " + MAX_PIXELS + ")");

            // ── 프레임 합성 + 축소 + 밝기변환 + 기록 ──────────────────
            BufferedImage canvas = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);

            try (BufferedWriter out = Files.newBufferedWriter(Path.of(output), StandardCharsets.UTF_8)) {
                out.write(w + "*" + h + "\n");
                boolean first = true;
                StringBuilder line = new StringBuilder(w * h * 3);

                for (int i = 0; i < frames; i++) {
                    BufferedImage sub = reader.read(i);
                    int[] info = frameInfo(reader, i); // {left, top, disposalCode}
                    int left = info[0], top = info[1], disposal = info[2];

                    BufferedImage saved = (disposal == 3) ? copy(canvas) : null; // restoreToPrevious 대비

                    // 1) 캔버스에 이번 프레임 덧그리기
                    Graphics2D g = canvas.createGraphics();
                    g.drawImage(sub, left, top, null);
                    g.dispose();

                    // 2) 완성된 프레임을 목표 크기로 축소
                    BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D gs = small.createGraphics();
                    gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    gs.drawImage(canvas, 0, 0, w, h, null);
                    gs.dispose();

                    // 3) 밝기 변환 후 기록
                    line.setLength(0);
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            if (!first) line.append(',');
                            line.append(brightness(small.getRGB(x, y)));
                            first = false;
                        }
                    }
                    out.write(line.toString());

                    // 4) 폐기 규칙 적용 (다음 프레임 준비)
                    if (disposal == 2) {           // restoreToBackgroundColor: 영역 비우기
                        Graphics2D gc = canvas.createGraphics();
                        gc.setComposite(AlphaComposite.Clear);
                        gc.fillRect(left, top, sub.getWidth(), sub.getHeight());
                        gc.dispose();
                    } else if (disposal == 3) {    // restoreToPrevious: 되돌리기
                        canvas = saved;
                    }
                    if ((i + 1) % 20 == 0) System.out.print("\r변환 중... " + (i + 1) + "/" + frames);
                }
            }
            System.out.println("\r변환 완료!                       ");
            System.out.println("  파일   : " + Path.of(output).toAbsolutePath());
            System.out.println("  프레임 : " + frames + "개 (" + w + "*" + h + ")");
            System.out.println("재생     : java Player.java \"" + Path.of(output).toAbsolutePath() + "\"");
        } finally {
            reader.dispose();
        }
    }

    /** ARGB 픽셀 -> 0~99 밝기. 투명도는 검은 배경에 올린 것으로 처리. */
    static int brightness(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        double lum = 0.299 * r + 0.587 * g + 0.114 * b; // 0~255
        double eff = lum * (a / 255.0);                 // 알파(투명) 반영
        int v = (int) Math.round(eff * 99 / 255);
        return Math.max(0, Math.min(99, v));
    }

    /** GIF 논리화면 크기. 스트림 메타데이터 우선, 없으면 프레임 경계의 합집합. */
    static int[] canvasSize(ImageReader reader, int frames) throws IOException {
        try {
            IIOMetadata sm = reader.getStreamMetadata();
            if (sm != null) {
                IIOMetadataNode root = (IIOMetadataNode) sm.getAsTree(sm.getNativeMetadataFormatName());
                IIOMetadataNode lsd = first(root, "LogicalScreenDescriptor");
                if (lsd != null) {
                    int w = parse(lsd.getAttribute("logicalScreenWidth"), 0);
                    int h = parse(lsd.getAttribute("logicalScreenHeight"), 0);
                    if (w > 0 && h > 0) return new int[]{w, h};
                }
            }
        } catch (Exception ignore) { /* 폴백으로 진행 */ }

        int maxR = 0, maxB = 0;
        for (int i = 0; i < frames; i++) {
            int[] info = frameInfo(reader, i);
            int fw = info[3], fh = info[4];
            maxR = Math.max(maxR, info[0] + fw);
            maxB = Math.max(maxB, info[1] + fh);
        }
        return new int[]{Math.max(1, maxR), Math.max(1, maxB)};
    }

    /** 프레임 메타데이터 -> {left, top, disposalCode, width, height}. disposalCode: 2=배경복원,3=이전복원,그외=유지 */
    static int[] frameInfo(ImageReader reader, int i) throws IOException {
        int left = 0, top = 0, disposal = 0, fw = 0, fh = 0;
        try {
            IIOMetadata meta = reader.getImageMetadata(i);
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(meta.getNativeMetadataFormatName());
            IIOMetadataNode desc = first(root, "ImageDescriptor");
            if (desc != null) {
                left = parse(desc.getAttribute("imageLeftPosition"), 0);
                top  = parse(desc.getAttribute("imageTopPosition"), 0);
                fw   = parse(desc.getAttribute("imageWidth"), 0);
                fh   = parse(desc.getAttribute("imageHeight"), 0);
            }
            IIOMetadataNode gce = first(root, "GraphicControlExtension");
            if (gce != null) {
                String d = gce.getAttribute("disposalMethod");
                if ("restoreToBackgroundColor".equals(d)) disposal = 2;
                else if ("restoreToPrevious".equals(d))   disposal = 3;
            }
        } catch (Exception ignore) { /* 기본값 사용 */ }
        return new int[]{left, top, disposal, fw, fh};
    }

    static IIOMetadataNode first(IIOMetadataNode root, String tag) {
        NodeList nl = root.getElementsByTagName(tag);
        return nl.getLength() > 0 ? (IIOMetadataNode) nl.item(0) : null;
    }

    static int parse(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    static BufferedImage copy(BufferedImage src) {
        BufferedImage b = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        Graphics2D g = b.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return b;
    }

    static String replaceExt(String path, String newExt) {
        int dot = path.lastIndexOf('.');
        int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (dot > sep) ? path.substring(0, dot) + newExt : path + newExt;
    }
}

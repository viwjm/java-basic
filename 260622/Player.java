import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CVID(문자 동영상) 플레이어.
 *
 * 실행법:
 *   java Player.java "C:\경로\영상파일.cvid"
 *
 * 동작:
 *   1. 첫 줄에서 "가로*세로" 를 읽어 동영상 크기를 알아낸다.
 *   2. 나머지 숫자들(쉼표 구분, 0~99)을 파싱해 3차원 배열로 만든다.
 *      배열 구조: video[프레임][세로 y][가로 x]
 *   3. while(true) 로 무한 반복하며, 밝기값을 문자로 바꿔 화면에 출력한다.
 */
public class Player {

    // 어두움 -> 밝음 순서의 문자 램프 (10단계).
    // 인덱스 0 = 가장 어두움(공백), 9 = 가장 밝음('@').
    static final char[] RAMP = {' ', '.', ':', '-', '=', '+', '*', '#', '%', '@'};

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.out.println("사용법: java Player.java \"<영상 파일 경로>\"");
            return;
        }

        // ── 1. 파일 읽기 ───────────────────────────────────────────────
        String content = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);

        // 첫 줄(헤더)과 나머지(본문) 분리
        int nl = content.indexOf('\n');
        if (nl < 0) {
            System.out.println("올바르지 않은 파일입니다: 헤더 줄을 찾을 수 없습니다.");
            return;
        }
        String header = content.substring(0, nl).trim();
        String body   = content.substring(nl + 1);

        // ── 2. 헤더에서 가로*세로 읽기 ────────────────────────────────
        String[] dim = header.split("\\*");
        int width  = Integer.parseInt(dim[0].trim());  // 가로
        int height = Integer.parseInt(dim[1].trim());  // 세로

        // ── 3. 본문 숫자 파싱 ─────────────────────────────────────────
        String[] tokens = body.split(",");
        List<Integer> nums = new ArrayList<>();
        for (String t : tokens) {
            t = t.trim();              // 앞뒤 공백/줄바꿈 제거
            if (t.isEmpty()) continue; // 마지막 쉼표 뒤 빈 토큰 등은 무시
            nums.add(Integer.parseInt(t));
        }

        int pixelsPerFrame = width * height;
        int frameCount = nums.size() / pixelsPerFrame;

        if (frameCount == 0) {
            System.out.println("프레임을 만들 수 없습니다. 숫자 개수를 확인하세요.");
            return;
        }

        // ── 4. 3차원 배열 만들기  video[프레임][y][x] ─────────────────
        int[][][] video = new int[frameCount][height][width];
        int idx = 0;
        for (int f = 0; f < frameCount; f++) {
            for (int y = 0; y < height; y++) {      // 위 -> 아래
                for (int x = 0; x < width; x++) {   // 왼쪽 -> 오른쪽
                    video[f][y][x] = nums.get(idx++);
                }
            }
        }

        // ── 5. 재생 ───────────────────────────────────────────────────
        // 시작 전에 화면을 한 번 깨끗이 비운다.
        //   \033[2J : 화면 전체 지우기 (ANSI 이스케이프 코드)
        System.out.print("\033[2J");

        while (true) {
            for (int f = 0; f < frameCount; f++) {
                StringBuilder sb = new StringBuilder();
                // \033[H : 커서를 좌상단(home)으로 옮긴다.
                // 이렇게 하면 프레임이 아래로 흘러내리지 않고
                // 같은 자리에 덮어써져서 애니메이션처럼 보인다.
                sb.append("\033[H");
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        sb.append(brightnessToChar(video[f][y][x]));
                    }
                    sb.append('\n');
                }
                System.out.print(sb);
                System.out.flush();

                Thread.sleep(100); // 한 프레임마다 0.1초 쉬기
            }
        }
    }

    /** 밝기값(0~99)을 문자 램프의 글자로 변환한다. */
    static char brightnessToChar(int value) {
        if (value < 0)  value = 0;
        if (value > 99) value = 99;
        int i = value * RAMP.length / 100; // 0~99 -> 0~(길이-1)
        if (i >= RAMP.length) i = RAMP.length - 1;
        return RAMP[i];
    }
}

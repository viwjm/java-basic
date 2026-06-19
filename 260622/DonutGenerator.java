import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 회전하는 3차원 도넛(토러스) CVID 영상 생성기 — "해석기하" 광선 추적 버전.
 *
 * donut.c 의 매개변수 스위핑 트릭을 쓰지 않고, 토러스의 실제 좌표방정식과
 * "직선(광선)과 곡면의 교점"을 직접 구해서 정직하게 그린다.
 *
 * 실행:
 *   java DonutGenerator.java                 -> 현재 폴더에 donut.cvid 생성
 *   java DonutGenerator.java "출력.cvid"
 * 재생:
 *   java Player.java donut.cvid
 *
 * ── 원리 (정직한 해석기하) ─────────────────────────────────────────
 *  · 토러스 음함수 방정식 (원점 중심, z축이 대칭축):
 *        F(x,y,z) = (√(x²+y²) − R)² + z² − r²
 *    F = 0 이면 표면, F > 0 이면 도넛 바깥, F < 0 이면 도넛 속.
 *  · 화면의 각 픽셀마다 카메라에서 광선(직선) 하나를 쏜다 (원근투영).
 *  · 도넛을 회전행렬 M 으로 돌린 모습을 그리려면, 광선을 M⁻¹ 로 역회전시켜
 *    "정자세 토러스" 좌표계로 옮긴 뒤 교점을 구하면 된다.
 *  · 교점은 광선을 따라 조금씩 전진(ray marching)하며 F 의 부호가
 *    + → − 로 바뀌는 첫 지점을 찾고, 이분법으로 다듬는다. (바보처럼 직접)
 *  · 그 점에서 표면 법선 = ∇F (기울기 벡터)를 해석적으로 계산한다:
 *        q = √(x²+y²)
 *        ∂F/∂x = 2(q−R)·x/q,  ∂F/∂y = 2(q−R)·y/q,  ∂F/∂z = 2z
 *  · 법선을 다시 M 으로 세계좌표로 돌린 뒤, 광원 방향과의 내적(람베르트)으로
 *    밝기를 정한다.
 */
public class DonutGenerator {

    // 영상 설정
    static final int W = 80, H = 40, FRAMES = 120;
    // 도넛 크기
    static final double R = 2.0;   // 큰 반지름 (도넛 중심 ~ 튜브 중심 거리)
    static final double r = 1.0;   // 작은 반지름 (튜브 굵기)
    // 카메라 / 화면
    static final double CAM  = 5.5;  // 카메라는 (0,0,-CAM) 에서 +z 를 바라본다
    static final double VIEW = 3.6;  // 화면 절반이 담는 월드 폭
    // 광선 전진 간격, 경계 구 반지름
    static final double DT = 0.02;
    static final double RB = R + r + 0.05;

    public static void main(String[] args) throws IOException {
        double[] light = normalize(new double[]{0, 1, -1}); // 광원: 위 + 카메라 쪽

        StringBuilder sb = new StringBuilder();
        sb.append(W).append('*').append(H).append('\n');
        boolean first = true;

        double perPixX = 2 * VIEW / W;   // 가로 픽셀당 월드 길이
        double perPixY = 2 * VIEW / H;   // 세로 픽셀당 월드 길이 (가로의 2배 -> 글자비율 보정)

        for (int f = 0; f < FRAMES; f++) {
            // 한 루프 동안 x축 1바퀴, y축 2바퀴 회전 -> 끊김 없이 반복
            double A = 2 * Math.PI * f / FRAMES;
            double B = 2 * Math.PI * 2 * f / FRAMES;
            double[][] M  = matMul(rotX(A), rotY(B)); // 도넛 자세(회전)
            double[][] Mi = transpose(M);             // 역회전 = 전치

            double[] O = {0, 0, -CAM};        // 카메라 위치 (세계좌표)
            double[] Ol = matVec(Mi, O);      // 정자세 좌표계에서의 카메라

            for (int py = 0; py < H; py++) {
                for (int px = 0; px < W; px++) {
                    // 픽셀 -> z=0 평면 위 목표점 -> 광선 방향(세계좌표)
                    double u = (px + 0.5 - W / 2.0) * perPixX;
                    double v = (H / 2.0 - (py + 0.5)) * perPixY;
                    double[] d  = normalize(new double[]{u, v, CAM}); // 목표점 - 카메라
                    double[] dl = matVec(Mi, d);                      // 정자세 좌표계로 역회전

                    int value = shadeRay(Ol, dl, M, light);
                    if (!first) sb.append(',');
                    sb.append(value);
                    first = false;
                }
            }
        }

        String outPath = (args.length >= 1) ? args[0] : "donut.cvid";
        Path out = Path.of(outPath);
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("도넛(해석기하 / 광선추적) 영상 생성 완료!");
        System.out.println("  파일 : " + out.toAbsolutePath());
        System.out.println("  크기 : " + W + "*" + H + ", 프레임 " + FRAMES + "개");
        System.out.println("재생   : java Player.java \"" + out.toAbsolutePath() + "\"");
    }

    /** 광선 하나를 쏴서 토러스와의 가장 가까운 교점을 찾고 밝기값(0~99)을 돌려준다. */
    static int shadeRay(double[] O, double[] d, double[][] M, double[] light) {
        // 1) 경계 구(반지름 RB)와의 교점으로 전진 구간 [t0, t1] 을 한정한다.
        double b = O[0]*d[0] + O[1]*d[1] + O[2]*d[2];          // O·d
        double c = O[0]*O[0] + O[1]*O[1] + O[2]*O[2] - RB*RB;  // |O|² − RB²
        double disc = b*b - c;
        if (disc < 0) return 0;            // 도넛 근처도 안 지나감 -> 배경
        double sq = Math.sqrt(disc);
        double t0 = -b - sq, t1 = -b + sq;
        if (t1 < 0) return 0;
        if (t0 < 0) t0 = 0;

        // 2) ray marching: F 부호가 + → − 로 바뀌는 첫 지점 찾기
        double prevF = F(at(O, d, t0)), prevT = t0;
        for (double t = t0 + DT; t <= t1; t += DT) {
            double f = F(at(O, d, t));
            if (prevF > 0 && f <= 0) {
                // 3) 이분법으로 교점 위치를 다듬는다.
                double lo = prevT, hi = t;
                for (int i = 0; i < 25; i++) {
                    double mid = (lo + hi) / 2;
                    if (F(at(O, d, mid)) > 0) lo = mid; else hi = mid;
                }
                double[] P = at(O, d, (lo + hi) / 2); // 교점 (정자세 좌표)

                // 4) 표면 법선 = ∇F, 정규화
                double q = Math.sqrt(P[0]*P[0] + P[1]*P[1]);
                double[] n = normalize(new double[]{2*(q-R)*P[0]/q, 2*(q-R)*P[1]/q, 2*P[2]});

                // 5) 세계좌표로 회전 후 람베르트 명암 -> 밝기값
                double[] nw = matVec(M, n);
                double shade = nw[0]*light[0] + nw[1]*light[1] + nw[2]*light[2];
                if (shade < 0) shade = 0;
                int val = (int) Math.round(12 + shade * 87); // 12('.') ~ 99('@')
                return Math.max(12, Math.min(99, val));
            }
            prevF = f; prevT = t;
        }
        return 0; // 토러스에 안 맞음 (가운데 구멍 등) -> 배경
    }

    /** 토러스 음함수 F(x,y,z) = (√(x²+y²) − R)² + z² − r² */
    static double F(double[] p) {
        double q = Math.sqrt(p[0]*p[0] + p[1]*p[1]) - R;
        return q*q + p[2]*p[2] - r*r;
    }

    static double[] at(double[] O, double[] d, double t) {
        return new double[]{O[0] + d[0]*t, O[1] + d[1]*t, O[2] + d[2]*t};
    }

    // ── 작은 선형대수 도우미 ──────────────────────────────────────────
    static double[] normalize(double[] v) {
        double len = Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
        return new double[]{v[0]/len, v[1]/len, v[2]/len};
    }
    static double[][] rotX(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{1,0,0},{0,c,-s},{0,s,c}};
    }
    static double[][] rotY(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{c,0,s},{0,1,0},{-s,0,c}};
    }
    static double[][] matMul(double[][] A, double[][] B) {
        double[][] C = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                double s = 0;
                for (int k = 0; k < 3; k++) s += A[i][k] * B[k][j];
                C[i][j] = s;
            }
        return C;
    }
    static double[][] transpose(double[][] A) {
        double[][] T = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) T[i][j] = A[j][i];
        return T;
    }
    static double[] matVec(double[][] M, double[] v) {
        return new double[]{
            M[0][0]*v[0] + M[0][1]*v[1] + M[0][2]*v[2],
            M[1][0]*v[0] + M[1][1]*v[1] + M[1][2]*v[2],
            M[2][0]*v[0] + M[2][1]*v[1] + M[2][2]*v[2]
        };
    }
}

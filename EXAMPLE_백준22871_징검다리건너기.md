# 📌 [백준] 22871번 - 징검다리 건너기 (large)

## 🔗 문제 링크

- [문제 바로가기](https://www.acmicpc.net/problem/22871)

## 💡 문제 해결 전략

이번 문제를 풀기 위해 **이분 탐색**과 **스택**을 같이 사용했다. 아이디어는 다음과 같다.

먼저 이분 탐색은 **최소 힘을 탐색**하는 데 사용된다. mid를 수정해 나가면서 어떤 값이 가장 최소 값인지 탐색하는 것이다.

그러면 mid가 최소값인지 판별하는 방법은 뭘까? 직접 경우의 수를 따져보는 것이다. 이때 **스택을 이용해 경우의 수를 확인**한다.

이분 탐색을 통해 mid가 6으로 설정되었다고 가정하자. 스택에는 배열의 인덱스를 넣어준다.

1. 먼저 스택에 0을 넣고 방문 표시를 한다.
2. 0을 pop하고, 0으로부터 갈 수 있는 돌 중 힘이 6을 넘지 않는 돌들을 스택에 넣어준다. 스택에 넣을 때는 계속 방문 표시를 한다.
3. 3을 pop하고 3으로부터 갈 수 있는 돌들을 push한다.
4. 4를 pop한다. 4는 돌의 가장 마지막이므로 로직을 빠져나온다.
5. 이 과정을 스택이 빌 때까지 반복한다.

로직에서 빠져나온 후, 해당 mid의 힘으로 갈 수 있는 경로가 있는지 체크하고 이분 탐색의 start와 end를 수정해준다.

## 💻 코드

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        int N = Integer.parseInt(st.nextToken());
        int[] stones = new int[N];

        st = new StringTokenizer(br.readLine());

        for (int i = 0; i < N; i++) {
            stones[i] = Integer.parseInt(st.nextToken());
        }

        // 움직이기 위해선 최소 힘이 1은 필요하기 때문에 start를 1로 설정
        long start = 1;
        // 첫 번째 돌에서 바로 마지막 돌로 뛰는 경우를 가장 큰 값으로 설정
        // 이 경우보다 적은 힘으로 갈 수 있는 경우를 찾으면 된다.
        long end = (long)(N - 1) * (long)(1 + Math.abs(stones[N - 1] - stones[0]));
        long answer = 0;

        while (start <= end) {
            long mid = (start + end) / 2;

            boolean[] visited = new boolean[N];
            boolean flag = false;

            Deque<Integer> stack = new ArrayDeque<>();
            stack.push(0);
            visited[0] = true;

            while (!stack.isEmpty()) {
                Integer pop = stack.pop();

                // 방문한 돌이 마지막 돌인 경우 while문 종료
                if (pop == N - 1) {
                    flag = true;
                    break;
                }

                // 현재 있는 돌보다 앞에 있는 돌로 움직일 때 드는 힘이 mid보다 작은 경우만 stack에 저장
                for (int i = pop + 1; i < N; i++) {
                    long power = (long)(i - pop) * (long)(1 + Math.abs(stones[i] - stones[pop]));
                    if (!visited[i] && power <= mid) {
                        stack.push(i);
                        visited[i] = true;
                    }
                }
            }

            if (flag) {
                answer = mid;
                end = mid - 1;
            } else {
                start = mid + 1;
            }
        }

        System.out.println(answer);
    }
}
```

## 📝 비고

- 코드에서 주의할 점은 **오버플로우**이다. 계속 제출했을 때 틀렸다고 나와서 처음에는 로직의 문제인 줄 알았다. 그러나 절댓값 계산이나 힘 계산 과정에서 int로 계산 후 long으로 값을 주기 때문에, int 계산 과정에서 오버플로우가 날 수 있다는 것을 간과하고 있었다.
- 이분 탐색 과정에서는 큰 값을 더하는 경우가 많아서 항상 오버플로우에 주의해야 한다.

- [x] 스스로 해결
- [ ] 답을 보고 해결
- [ ] 못 풀었음

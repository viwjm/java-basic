# 📌 문제 이름

프로그래머스 181834번 - l로 만들기

## 🔗 문제 링크

https://school.programmers.co.kr/learn/courses/30/lessons/181834

## 💡 문제 해결 전략

일단 for문을 써야 한다는 것을 알 수 있다.
문제는 문자열 다루는 함수를 못 외우고 있다는 것인데 그것은 인텔리제이 자동 완성이 해결해 주었다.

## 💻 코드

```java
class Solution {
    public String solution(String myString) {
        StringBuilder answer = new StringBuilder();

        for (int i = 0; i < myString.length(); i++) {
            char c = myString.charAt(i) < 'l' ? 'l' : myString.charAt(i);
            answer.append(c);
        }

        return answer.toString();
    }
}
```

## 📝 비고

- [x] 스스로 해결
- [ ] 답을 보고 해결
- [ ] 못 풀었음

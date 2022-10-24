package pub.telephone.javapromise.async.promise;

import java.time.Duration;

public interface PromiseTimeOutListener {
    void OnTimeOut(Duration duration);
}

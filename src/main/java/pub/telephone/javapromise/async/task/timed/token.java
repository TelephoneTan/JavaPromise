package pub.telephone.javapromise.async.task.timed;

import java.time.Duration;

public class token {
    Duration delay;

    public token setDelay(Duration delay) {
        this.delay = delay;
        return this;
    }
}

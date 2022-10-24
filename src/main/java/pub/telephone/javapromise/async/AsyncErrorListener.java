package pub.telephone.javapromise.async;

import org.jetbrains.annotations.Nullable;

public interface AsyncErrorListener {
    void OnError(@Nullable Throwable e);
}

package pub.telephone.javapromise.async.task.versioned;

public class VersionedResult<T> {
    public final int Version;
    public final T Result;

    public VersionedResult(int version, T result) {
        Version = version;
        Result = result;
    }
}

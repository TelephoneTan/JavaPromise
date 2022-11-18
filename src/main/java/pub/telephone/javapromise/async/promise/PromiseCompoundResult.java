package pub.telephone.javapromise.async.promise;

import java.util.List;

public class PromiseCompoundResult<R, O> {
    public List<R> RequiredValueList;
    public List<O> OptionalValueList;
    public Throwable[] OptionalReasonList;
    public boolean[] OptionalCancelFlagList;
    public boolean[] OptionalSuccessFlagList;

    public PromiseCompoundResult<R, O> setRequiredValueList(List<R> requiredValueList) {
        RequiredValueList = requiredValueList;
        return this;
    }

    public PromiseCompoundResult<R, O> setOptionalValueList(List<O> optionalValueList) {
        OptionalValueList = optionalValueList;
        return this;
    }

    public PromiseCompoundResult<R, O> setOptionalReasonList(Throwable[] optionalReasonList) {
        OptionalReasonList = optionalReasonList;
        return this;
    }

    public PromiseCompoundResult<R, O> setOptionalCancelFlagList(boolean[] optionalCancelFlagList) {
        OptionalCancelFlagList = optionalCancelFlagList;
        return this;
    }

    public PromiseCompoundResult<R, O> setOptionalSuccessFlagList(boolean[] optionalSuccessFlagList) {
        OptionalSuccessFlagList = optionalSuccessFlagList;
        return this;
    }
}

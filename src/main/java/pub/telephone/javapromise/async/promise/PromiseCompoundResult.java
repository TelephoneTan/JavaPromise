package pub.telephone.javapromise.async.promise;

public class PromiseCompoundResult {
    public Object[] RequiredValueList;
    public Object[] OptionalValueList;
    public Throwable[] OptionalReasonList;
    public boolean[] OptionalCancelFlagList;
    public boolean[] OptionalSuccessFlagList;

    public PromiseCompoundResult setRequiredValueList(Object[] requiredValueList) {
        RequiredValueList = requiredValueList;
        return this;
    }

    public PromiseCompoundResult setOptionalValueList(Object[] optionalValueList) {
        OptionalValueList = optionalValueList;
        return this;
    }

    public PromiseCompoundResult setOptionalReasonList(Throwable[] optionalReasonList) {
        OptionalReasonList = optionalReasonList;
        return this;
    }

    public PromiseCompoundResult setOptionalCancelFlagList(boolean[] optionalCancelFlagList) {
        OptionalCancelFlagList = optionalCancelFlagList;
        return this;
    }

    public PromiseCompoundResult setOptionalSuccessFlagList(boolean[] optionalSuccessFlagList) {
        OptionalSuccessFlagList = optionalSuccessFlagList;
        return this;
    }
}

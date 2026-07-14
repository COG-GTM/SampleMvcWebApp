namespace GenericLibsBase.Core
{
    /// <summary>
    /// A success/error status that also carries a typed result.
    /// </summary>
    public interface ISuccessOrErrors<T> : ISuccessOrErrors
    {
        /// <summary>The result value (only meaningful when <see cref="ISuccessOrErrors.IsValid"/> is true).</summary>
        T Result { get; }

        /// <summary>Sets the result and a success message.</summary>
        ISuccessOrErrors<T> SetSuccessWithResult(T result, string successformat, params object[] args);
    }
}

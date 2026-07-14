namespace GenericLibsBase.Core
{
    /// <summary>
    /// Default implementation of <see cref="ISuccessOrErrors{T}"/>.
    /// </summary>
    public class SuccessOrErrors<T> : SuccessOrErrors, ISuccessOrErrors<T>
    {
        public T Result { get; private set; }

        public ISuccessOrErrors<T> SetSuccessWithResult(T result, string successformat, params object[] args)
        {
            Result = result;
            SetSuccessMessage(successformat, args);
            return this;
        }

        /// <summary>
        /// Builds an errored <see cref="ISuccessOrErrors{T}"/> from a non-generic status.
        /// The result stays at its default value; errors/warnings are copied over.
        /// </summary>
        public static ISuccessOrErrors<T> ConvertNonResultStatus(ISuccessOrErrors status)
        {
            var result = new SuccessOrErrors<T>();
            result.Combine(status);
            return result;
        }
    }
}

using System.Collections.Generic;
using System.Linq;

namespace SampleWebApp.Core.Common.Results
{
    public interface ISuccessOrErrors
    {
        bool IsValid { get; }
        string SuccessMessage { get; }
        IReadOnlyList<ValidationError> Errors { get; }
        void AddNamedParameterError(string parameterName, string errorMessage);
        string ErrorsAsHtml();
    }

    public interface ISuccessOrErrors<T> : ISuccessOrErrors
    {
        T Result { get; }
    }

    public class SuccessOrErrors : ISuccessOrErrors
    {
        private readonly List<ValidationError> _errors = new List<ValidationError>();

        public bool IsValid => !_errors.Any();
        public string SuccessMessage { get; private set; }
        public IReadOnlyList<ValidationError> Errors => _errors.AsReadOnly();

        protected SuccessOrErrors() { }

        public static SuccessOrErrors Success(string message)
        {
            return new SuccessOrErrors { SuccessMessage = message };
        }

        public static SuccessOrErrors FailSingleError(string errorMessage, string propertyName = null)
        {
            var result = new SuccessOrErrors();
            result._errors.Add(new ValidationError(propertyName, errorMessage));
            return result;
        }

        public void AddNamedParameterError(string parameterName, string errorMessage)
        {
            _errors.Add(new ValidationError(parameterName, errorMessage));
        }

        public string ErrorsAsHtml()
        {
            return string.Join("<br/>", _errors.Select(e => e.ErrorMessage));
        }
    }

    public class SuccessOrErrors<T> : ISuccessOrErrors<T>
    {
        private readonly List<ValidationError> _errors = new List<ValidationError>();

        public bool IsValid => !_errors.Any();
        public string SuccessMessage { get; private set; }
        public T Result { get; private set; }
        public IReadOnlyList<ValidationError> Errors => _errors.AsReadOnly();

        private SuccessOrErrors() { }

        public static SuccessOrErrors<T> Success(T result, string message = null)
        {
            return new SuccessOrErrors<T>
            {
                Result = result,
                SuccessMessage = message
            };
        }

        public static SuccessOrErrors<T> FailSingleError(string errorMessage, string propertyName = null)
        {
            var result = new SuccessOrErrors<T>();
            result._errors.Add(new ValidationError(propertyName, errorMessage));
            return result;
        }

        public static SuccessOrErrors<T> ConvertNonResultStatus(ISuccessOrErrors status)
        {
            var result = new SuccessOrErrors<T>();
            foreach (var error in status.Errors)
            {
                result._errors.Add(error);
            }
            return result;
        }

        public void AddNamedParameterError(string parameterName, string errorMessage)
        {
            _errors.Add(new ValidationError(parameterName, errorMessage));
        }

        public string ErrorsAsHtml()
        {
            return string.Join("<br/>", _errors.Select(e => e.ErrorMessage));
        }
    }
}

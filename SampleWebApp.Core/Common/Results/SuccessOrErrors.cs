using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SampleWebApp.Core.Common.Results
{
    public interface ISuccessOrErrors
    {
        bool IsValid { get; }
        string SuccessMessage { get; }
        IReadOnlyList<ValidationError> Errors { get; }
        string ErrorsAsHtml();
    }

    public interface ISuccessOrErrors<T> : ISuccessOrErrors
    {
        T Result { get; }
    }

    public class ValidationError
    {
        public string PropertyName { get; set; }
        public string ErrorMessage { get; set; }

        public ValidationError(string propertyName, string errorMessage)
        {
            PropertyName = propertyName;
            ErrorMessage = errorMessage;
        }

        public ValidationError(string errorMessage)
        {
            PropertyName = string.Empty;
            ErrorMessage = errorMessage;
        }
    }

    public class SuccessOrErrors : ISuccessOrErrors
    {
        private readonly List<ValidationError> _errors;

        public bool IsValid => _errors.Count == 0;
        public string SuccessMessage { get; private set; }
        public IReadOnlyList<ValidationError> Errors => _errors.AsReadOnly();

        protected SuccessOrErrors()
        {
            _errors = new List<ValidationError>();
        }

        public static SuccessOrErrors Success(string message)
        {
            return new SuccessOrErrors { SuccessMessage = message };
        }

        public static SuccessOrErrors FailSingleError(string errorMessage)
        {
            var result = new SuccessOrErrors();
            result._errors.Add(new ValidationError(errorMessage));
            return result;
        }

        public static SuccessOrErrors FailSingleError(string propertyName, string errorMessage)
        {
            var result = new SuccessOrErrors();
            result._errors.Add(new ValidationError(propertyName, errorMessage));
            return result;
        }

        public void AddNamedParameterError(string propertyName, string errorMessage)
        {
            _errors.Add(new ValidationError(propertyName, errorMessage));
        }

        public void AddError(string errorMessage)
        {
            _errors.Add(new ValidationError(errorMessage));
        }

        public string ErrorsAsHtml()
        {
            if (!_errors.Any())
                return string.Empty;

            var sb = new StringBuilder();
            sb.Append("<ul>");
            foreach (var error in _errors)
            {
                sb.Append("<li>");
                if (!string.IsNullOrEmpty(error.PropertyName))
                    sb.Append($"{error.PropertyName}: ");
                sb.Append(error.ErrorMessage);
                sb.Append("</li>");
            }
            sb.Append("</ul>");
            return sb.ToString();
        }
    }

    public class SuccessOrErrors<T> : SuccessOrErrors, ISuccessOrErrors<T>
    {
        public T Result { get; private set; }

        private SuccessOrErrors() { }

        public static SuccessOrErrors<T> Success(T result, string message = null)
        {
            return new SuccessOrErrors<T>
            {
                Result = result
            };
        }

        public new static SuccessOrErrors<T> FailSingleError(string errorMessage)
        {
            var result = new SuccessOrErrors<T>();
            result.AddError(errorMessage);
            return result;
        }

        public new static SuccessOrErrors<T> FailSingleError(string propertyName, string errorMessage)
        {
            var result = new SuccessOrErrors<T>();
            result.AddNamedParameterError(propertyName, errorMessage);
            return result;
        }

        public static SuccessOrErrors<T> ConvertNonResultStatus(ISuccessOrErrors status)
        {
            var result = new SuccessOrErrors<T>();
            foreach (var error in status.Errors)
            {
                result.AddNamedParameterError(error.PropertyName, error.ErrorMessage);
            }
            return result;
        }
    }
}

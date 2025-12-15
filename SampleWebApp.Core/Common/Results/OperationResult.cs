using System.Collections.Generic;
using System.Linq;

namespace SampleWebApp.Core.Common.Results
{
    public class OperationResult
    {
        public bool IsValid => !Errors.Any();
        public string SuccessMessage { get; protected set; }
        public List<ValidationError> Errors { get; protected set; } = new List<ValidationError>();

        protected OperationResult() { }

        public static OperationResult Success(string message = null)
        {
            return new OperationResult { SuccessMessage = message };
        }

        public static OperationResult Failure(string errorMessage, string propertyName = null)
        {
            var result = new OperationResult();
            result.Errors.Add(new ValidationError(propertyName, errorMessage));
            return result;
        }

        public static OperationResult Failure(IEnumerable<ValidationError> errors)
        {
            var result = new OperationResult();
            result.Errors.AddRange(errors);
            return result;
        }

        public void AddError(string errorMessage, string propertyName = null)
        {
            Errors.Add(new ValidationError(propertyName, errorMessage));
        }

        public string ErrorsAsHtml()
        {
            return string.Join("<br/>", Errors.Select(e => e.ErrorMessage));
        }
    }

    public class ValidationError
    {
        public string PropertyName { get; }
        public string ErrorMessage { get; }

        public ValidationError(string propertyName, string errorMessage)
        {
            PropertyName = propertyName;
            ErrorMessage = errorMessage;
        }
    }
}

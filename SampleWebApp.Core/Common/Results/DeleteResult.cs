using System.Collections.Generic;

namespace SampleWebApp.Core.Common.Results
{
    public class DeleteResult : OperationResult
    {
        private DeleteResult() { }

        public new static DeleteResult Success(string message = null)
        {
            return new DeleteResult { SuccessMessage = message };
        }

        public new static DeleteResult Failure(string errorMessage, string propertyName = null)
        {
            var result = new DeleteResult();
            result.Errors.Add(new ValidationError(propertyName, errorMessage));
            return result;
        }

        public new static DeleteResult Failure(IEnumerable<ValidationError> errors)
        {
            var result = new DeleteResult();
            result.Errors.AddRange(errors);
            return result;
        }
    }
}

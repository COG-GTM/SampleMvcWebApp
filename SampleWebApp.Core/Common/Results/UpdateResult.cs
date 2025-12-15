using System.Collections.Generic;

namespace SampleWebApp.Core.Common.Results
{
    public class UpdateResult : OperationResult
    {
        private UpdateResult() { }

        public new static UpdateResult Success(string message = null)
        {
            return new UpdateResult { SuccessMessage = message };
        }

        public new static UpdateResult Failure(string errorMessage, string propertyName = null)
        {
            var result = new UpdateResult();
            result.Errors.Add(new ValidationError(propertyName, errorMessage));
            return result;
        }

        public new static UpdateResult Failure(IEnumerable<ValidationError> errors)
        {
            var result = new UpdateResult();
            result.Errors.AddRange(errors);
            return result;
        }
    }
}

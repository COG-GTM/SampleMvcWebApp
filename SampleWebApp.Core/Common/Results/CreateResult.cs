using System.Collections.Generic;

namespace SampleWebApp.Core.Common.Results
{
    public class CreateResult : OperationResult
    {
        public int CreatedId { get; private set; }

        private CreateResult() { }

        public static CreateResult Success(string message, int createdId = 0)
        {
            return new CreateResult
            {
                SuccessMessage = message,
                CreatedId = createdId
            };
        }

        public new static CreateResult Failure(string errorMessage, string propertyName = null)
        {
            var result = new CreateResult();
            result.Errors.Add(new ValidationError(propertyName, errorMessage));
            return result;
        }

        public new static CreateResult Failure(IEnumerable<ValidationError> errors)
        {
            var result = new CreateResult();
            result.Errors.AddRange(errors);
            return result;
        }
    }
}

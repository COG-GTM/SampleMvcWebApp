using System.Collections.Generic;

namespace SampleWebApp.Core.Common.Results
{
    public class CreateResult
    {
        public bool IsValid => Errors.Count == 0;
        public string SuccessMessage { get; private set; }
        public List<string> Errors { get; private set; }
        public int CreatedId { get; private set; }

        private CreateResult()
        {
            Errors = new List<string>();
        }

        public static CreateResult Success(string message, int createdId = 0)
        {
            return new CreateResult
            {
                SuccessMessage = message,
                CreatedId = createdId
            };
        }

        public static CreateResult Fail(string error)
        {
            var result = new CreateResult();
            result.Errors.Add(error);
            return result;
        }

        public static CreateResult Fail(IEnumerable<string> errors)
        {
            var result = new CreateResult();
            result.Errors.AddRange(errors);
            return result;
        }
    }
}

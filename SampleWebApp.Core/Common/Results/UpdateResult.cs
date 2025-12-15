using System.Collections.Generic;

namespace SampleWebApp.Core.Common.Results
{
    public class UpdateResult
    {
        public bool IsValid => Errors.Count == 0;
        public string SuccessMessage { get; private set; }
        public List<string> Errors { get; private set; }

        private UpdateResult()
        {
            Errors = new List<string>();
        }

        public static UpdateResult Success(string message)
        {
            return new UpdateResult
            {
                SuccessMessage = message
            };
        }

        public static UpdateResult Fail(string error)
        {
            var result = new UpdateResult();
            result.Errors.Add(error);
            return result;
        }

        public static UpdateResult Fail(IEnumerable<string> errors)
        {
            var result = new UpdateResult();
            result.Errors.AddRange(errors);
            return result;
        }
    }
}

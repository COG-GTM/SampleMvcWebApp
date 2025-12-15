using System.Collections.Generic;

namespace SampleWebApp.Core.Common.Results
{
    public class DeleteResult
    {
        public bool IsValid => Errors.Count == 0;
        public string SuccessMessage { get; private set; }
        public List<string> Errors { get; private set; }

        private DeleteResult()
        {
            Errors = new List<string>();
        }

        public static DeleteResult Success(string message)
        {
            return new DeleteResult
            {
                SuccessMessage = message
            };
        }

        public static DeleteResult Fail(string error)
        {
            var result = new DeleteResult();
            result.Errors.Add(error);
            return result;
        }

        public static DeleteResult Fail(IEnumerable<string> errors)
        {
            var result = new DeleteResult();
            result.Errors.AddRange(errors);
            return result;
        }
    }
}

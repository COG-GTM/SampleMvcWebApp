using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;

namespace GenericLibsBase.Core
{
    /// <summary>
    /// Default implementation of <see cref="ISuccessOrErrors"/>.
    /// </summary>
    public class SuccessOrErrors : ISuccessOrErrors
    {
        private readonly List<ValidationResult> _errors = new List<ValidationResult>();
        private readonly List<string> _warnings = new List<string>();
        private string _successMessage = string.Empty;

        private static string Format(string format, object[] args)
        {
            //When there are no args the string is used verbatim, so messages containing
            //braces (e.g. validation messages) are never re-parsed by string.Format.
            return args == null || args.Length == 0 ? format : string.Format(format, args);
        }

        public bool IsValid
        {
            get { return _errors.Count == 0; }
        }

        public IReadOnlyList<ValidationResult> Errors
        {
            get { return _errors; }
        }

        public IReadOnlyList<string> Warnings
        {
            get { return _warnings; }
        }

        public bool HasWarnings
        {
            get { return _warnings.Count > 0; }
        }

        public string SuccessMessage
        {
            get { return IsValid ? _successMessage : string.Empty; }
        }

        public ISuccessOrErrors AddSingleError(string errorformat, params object[] args)
        {
            _errors.Add(new ValidationResult(Format(errorformat, args)));
            return this;
        }

        public ISuccessOrErrors AddNamedParameterError(string parameterName, string errorformat, params object[] args)
        {
            _errors.Add(new ValidationResult(Format(errorformat, args), new[] { parameterName }));
            return this;
        }

        public ISuccessOrErrors AddWarning(string warningformat, params object[] args)
        {
            _warnings.Add("Warning: " + Format(warningformat, args));
            return this;
        }

        public ISuccessOrErrors Combine(ISuccessOrErrors objToCopyErrorsFrom)
        {
            if (objToCopyErrorsFrom == null) return this;
            _errors.AddRange(objToCopyErrorsFrom.Errors);
            _warnings.AddRange(objToCopyErrorsFrom.Warnings);
            return this;
        }

        public ISuccessOrErrors SetSuccessMessage(string successformat, params object[] args)
        {
            _successMessage = Format(successformat, args);
            return this;
        }

        public string ErrorsAsHtml()
        {
            if (IsValid) return string.Empty;
            var lines = _errors.Select(x => "<li>" + System.Net.WebUtility.HtmlEncode(x.ErrorMessage) + "</li>");
            return "<ul>" + string.Join(string.Empty, lines) + "</ul>";
        }

        public override string ToString()
        {
            return IsValid
                ? (string.IsNullOrEmpty(_successMessage) ? "Successful" : _successMessage)
                : string.Format("Failed with {0} error(s)", _errors.Count);
        }

        /// <summary>Creates a successful status with the given message.</summary>
        public static ISuccessOrErrors Success(string successformat, params object[] args)
        {
            return new SuccessOrErrors().SetSuccessMessage(successformat, args);
        }

        /// <summary>Creates a status carrying a single global error.</summary>
        public static ISuccessOrErrors SingleError(string errorformat, params object[] args)
        {
            return new SuccessOrErrors().AddSingleError(errorformat, args);
        }
    }
}

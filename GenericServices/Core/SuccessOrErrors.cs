using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Net;
using System.Text;

namespace GenericLibsBase.Core
{
    /// <summary>
    /// Non generic status result used throughout the service layer.
    /// Compatibility replacement for the original GenericLibsBase ISuccessOrErrors.
    /// </summary>
    public interface ISuccessOrErrors
    {
        /// <summary>
        /// True if the operation completed with no errors and a success message has been set.
        /// </summary>
        bool IsValid { get; }

        /// <summary>
        /// The success message (only meaningful when IsValid is true).
        /// </summary>
        string SuccessMessage { get; }

        /// <summary>
        /// The list of errors. Each error may carry the names of the members it relates to.
        /// </summary>
        IReadOnlyList<ValidationResult> Errors { get; }

        /// <summary>
        /// Adds an error that is not associated with a specific property.
        /// </summary>
        ISuccessOrErrors AddSingleError(string errorformat, params object[] args);

        /// <summary>
        /// Adds an error associated with the named property/parameter.
        /// </summary>
        ISuccessOrErrors AddNamedParameterError(string parameterName, string errorformat, params object[] args);

        /// <summary>
        /// Sets the success message and marks the result as valid (assuming no errors).
        /// </summary>
        ISuccessOrErrors SetSuccessMessage(string successformat, params object[] args);

        /// <summary>
        /// Returns the errors as a simple html (br separated) string.
        /// </summary>
        string ErrorsAsHtml();
    }

    /// <summary>
    /// Generic version that also carries a result of type T.
    /// </summary>
    public interface ISuccessOrErrors<T> : ISuccessOrErrors
    {
        T Result { get; }

        ISuccessOrErrors<T> SetSuccessWithResult(T result, string successformat, params object[] args);
    }

    /// <summary>
    /// Default implementation of <see cref="ISuccessOrErrors"/>.
    /// </summary>
    public class SuccessOrErrors : ISuccessOrErrors
    {
        private readonly List<ValidationResult> _errors = new List<ValidationResult>();
        private bool _hasBeenSuccessfullySet;
        private string _successMessage = string.Empty;

        public IReadOnlyList<ValidationResult> Errors => _errors;

        public bool IsValid => _hasBeenSuccessfullySet && _errors.Count == 0;

        public string SuccessMessage => IsValid ? _successMessage : string.Empty;

        public ISuccessOrErrors AddSingleError(string errorformat, params object[] args)
        {
            _errors.Add(new ValidationResult(string.Format(errorformat, args)));
            return this;
        }

        public ISuccessOrErrors AddNamedParameterError(string parameterName, string errorformat, params object[] args)
        {
            _errors.Add(new ValidationResult(string.Format(errorformat, args), new[] { parameterName }));
            return this;
        }

        public ISuccessOrErrors SetSuccessMessage(string successformat, params object[] args)
        {
            _successMessage = string.Format(successformat, args);
            _hasBeenSuccessfullySet = true;
            return this;
        }

        public string ErrorsAsHtml()
        {
            var sb = new StringBuilder();
            foreach (var error in _errors)
            {
                if (sb.Length > 0)
                    sb.Append("<br/>");
                sb.Append(WebUtility.HtmlEncode(error.ErrorMessage));
            }
            return sb.ToString();
        }

        internal void AddValidationResults(IEnumerable<ValidationResult> results)
        {
            _errors.AddRange(results);
        }

        /// <summary>
        /// Creates a new, valid status with the given success message.
        /// </summary>
        public static ISuccessOrErrors Success(string successformat, params object[] args)
        {
            return new SuccessOrErrors().SetSuccessMessage(successformat, args);
        }
    }

    /// <summary>
    /// Default implementation of <see cref="ISuccessOrErrors{T}"/>.
    /// </summary>
    public class SuccessOrErrors<T> : ISuccessOrErrors<T>
    {
        private readonly List<ValidationResult> _errors = new List<ValidationResult>();
        private bool _hasBeenSuccessfullySet;
        private string _successMessage = string.Empty;

        public T Result { get; private set; }

        public IReadOnlyList<ValidationResult> Errors => _errors;

        public bool IsValid => _hasBeenSuccessfullySet && _errors.Count == 0;

        public string SuccessMessage => IsValid ? _successMessage : string.Empty;

        public ISuccessOrErrors AddSingleError(string errorformat, params object[] args)
        {
            _errors.Add(new ValidationResult(string.Format(errorformat, args)));
            return this;
        }

        public ISuccessOrErrors AddNamedParameterError(string parameterName, string errorformat, params object[] args)
        {
            _errors.Add(new ValidationResult(string.Format(errorformat, args), new[] { parameterName }));
            return this;
        }

        public ISuccessOrErrors SetSuccessMessage(string successformat, params object[] args)
        {
            _successMessage = string.Format(successformat, args);
            _hasBeenSuccessfullySet = true;
            return this;
        }

        public ISuccessOrErrors<T> SetSuccessWithResult(T result, string successformat, params object[] args)
        {
            Result = result;
            _successMessage = string.Format(successformat, args);
            _hasBeenSuccessfullySet = true;
            return this;
        }

        public string ErrorsAsHtml()
        {
            var sb = new StringBuilder();
            foreach (var error in _errors)
            {
                if (sb.Length > 0)
                    sb.Append("<br/>");
                sb.Append(WebUtility.HtmlEncode(error.ErrorMessage));
            }
            return sb.ToString();
        }

        internal void AddValidationResults(IEnumerable<ValidationResult> results)
        {
            _errors.AddRange(results);
        }

        /// <summary>
        /// Copies the errors from a non result status into a new typed status (with default result).
        /// </summary>
        public static ISuccessOrErrors<T> ConvertNonResultStatus(ISuccessOrErrors status)
        {
            var result = new SuccessOrErrors<T>();
            result.AddValidationResults(status.Errors);
            return result;
        }
    }
}

using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;

namespace GenericLibsBase.Core
{
    /// <summary>
    /// Carries the success/error outcome of an operation, including a list of
    /// <see cref="ValidationResult"/> errors (each possibly associated with member names)
    /// and an optional success message.
    /// </summary>
    public interface ISuccessOrErrors
    {
        /// <summary>
        /// True if there are no errors. (A status must have been given a success message
        /// or had errors added before this is meaningful.)
        /// </summary>
        bool IsValid { get; }

        /// <summary>The validation errors, if any.</summary>
        IReadOnlyList<ValidationResult> Errors { get; }

        /// <summary>Optional non-error warnings.</summary>
        IReadOnlyList<string> Warnings { get; }

        bool HasWarnings { get; }

        /// <summary>The success message set via <see cref="SetSuccessMessage"/>.</summary>
        string SuccessMessage { get; }

        /// <summary>Adds a global error (no member name).</summary>
        ISuccessOrErrors AddSingleError(string errorformat, params object[] args);

        /// <summary>Adds an error associated with the named property/parameter.</summary>
        ISuccessOrErrors AddNamedParameterError(string parameterName, string errorformat, params object[] args);

        /// <summary>Adds a warning message.</summary>
        ISuccessOrErrors AddWarning(string warningformat, params object[] args);

        /// <summary>Copies the errors and warnings from another status into this one.</summary>
        ISuccessOrErrors Combine(ISuccessOrErrors objToCopyErrorsFrom);

        /// <summary>Sets the success message. Only meaningful when there are no errors.</summary>
        ISuccessOrErrors SetSuccessMessage(string successformat, params object[] args);

        /// <summary>Returns the errors formatted as an HTML unordered list.</summary>
        string ErrorsAsHtml();
    }
}

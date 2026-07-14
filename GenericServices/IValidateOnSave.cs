using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;

namespace GenericServices
{
    /// <summary>
    /// Implemented by a DbContext that needs to run extra, database-level validation on
    /// entities being added/modified (e.g. cross-row uniqueness checks). This replaces the
    /// EF6 <c>DbContext.ValidateEntity</c> override, which EF Core does not provide.
    /// </summary>
    public interface IValidateOnSave
    {
        /// <summary>
        /// Returns any database-level validation errors for the given entity, or an empty
        /// sequence if it is valid.
        /// </summary>
        IEnumerable<ValidationResult> ValidateEntity(object entity);
    }
}

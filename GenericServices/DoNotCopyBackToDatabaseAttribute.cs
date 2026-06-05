using System;

namespace GenericServices
{
    /// <summary>
    /// Marks a DTO property that should NOT be copied back to the database entity
    /// when creating or updating (e.g. values managed by the data layer such as LastUpdated).
    /// Compatibility replacement for the original GenericServices attribute.
    /// </summary>
    [AttributeUsage(AttributeTargets.Property, AllowMultiple = false)]
    public class DoNotCopyBackToDatabaseAttribute : Attribute
    {
    }
}

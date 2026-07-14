using System;

namespace GenericServices
{
    /// <summary>
    /// Marks a DTO property that must NOT be copied back onto the data entity when
    /// creating/updating (e.g. values the data layer maintains itself).
    /// </summary>
    [AttributeUsage(AttributeTargets.Property, AllowMultiple = false)]
    public sealed class DoNotCopyBackToDatabaseAttribute : Attribute
    {
    }
}

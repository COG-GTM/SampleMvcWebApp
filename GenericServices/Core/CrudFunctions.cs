using System;

namespace GenericServices.Core
{
    /// <summary>
    /// Flags describing which CRUD operations a DTO supports.
    /// Compatibility replacement for the original GenericServices CrudFunctions enum.
    /// </summary>
    [Flags]
    public enum CrudFunctions
    {
        None = 0,
        List = 1,
        Detail = 2,
        Create = 4,
        Update = 8,
        Delete = 16,
        AllCrud = List | Detail | Create | Update | Delete
    }
}

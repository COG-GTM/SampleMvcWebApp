using System;
using System.Threading.Tasks;
using GenericLibsBase.Core;

namespace GenericServices.Core
{
    /// <summary>
    /// Non-generic entry points the service layer uses to drive a DTO without knowing its
    /// concrete generic type. Implemented internally by the DTO base classes.
    /// </summary>
    internal interface IInternalDtoCrud
    {
        Type AssociatedEntityType { get; }
        object[] GetKeyValues();
        CrudFunctions GetSupportedFunctions();
    }

    internal interface ISyncInternalDtoCrud : IInternalDtoCrud
    {
        void RunSetupSecondaryData(IGenericServicesDbContext context);
        ISuccessOrErrors RunCreateDataFromDto(IGenericServicesDbContext context);
        ISuccessOrErrors RunUpdateDataFromDto(IGenericServicesDbContext context, object destinationEntity);
    }

    internal interface IAsyncInternalDtoCrud : IInternalDtoCrud
    {
        Task RunSetupSecondaryDataAsync(IGenericServicesDbContext context);
        Task<ISuccessOrErrors> RunCreateDataFromDtoAsync(IGenericServicesDbContext context);
        Task<ISuccessOrErrors> RunUpdateDataFromDtoAsync(IGenericServicesDbContext context, object destinationEntity);
    }
}

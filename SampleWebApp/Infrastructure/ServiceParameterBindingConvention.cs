using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ApplicationModels;
using Microsoft.AspNetCore.Mvc.ModelBinding;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

namespace SampleWebApp.Infrastructure
{
    /// <summary>
    /// The original MVC5 app used a custom model binder (DiModelBinder) so that services could be
    /// supplied as action-method parameters, e.g. <c>Index(IListService service)</c>. ASP.NET Core
    /// does not bind such parameters from DI by default, so this convention marks any action
    /// parameter whose type is a registered service as <see cref="BindingSource.Services"/>,
    /// preserving the original controller signatures.
    /// </summary>
    public class ServiceParameterBindingConvention : IActionModelConvention
    {
        private readonly IServiceProviderIsService _isService;

        public ServiceParameterBindingConvention(IServiceProviderIsService isService)
        {
            _isService = isService;
        }

        public void Apply(ActionModel action)
        {
            foreach (var parameter in action.Parameters)
            {
                if (parameter.BindingInfo?.BindingSource != null)
                    continue;

                if (_isService.IsService(parameter.ParameterInfo.ParameterType))
                {
                    parameter.BindingInfo ??= new BindingInfo();
                    parameter.BindingInfo.BindingSource = BindingSource.Services;
                }
            }
        }
    }

    /// <summary>
    /// Registers <see cref="ServiceParameterBindingConvention"/> once the DI container is available.
    /// </summary>
    public class ConfigureServiceParameterBinding : IConfigureOptions<MvcOptions>
    {
        private readonly IServiceProviderIsService _isService;

        public ConfigureServiceParameterBinding(IServiceProviderIsService isService)
        {
            _isService = isService;
        }

        public void Configure(MvcOptions options)
        {
            options.Conventions.Add(new ServiceParameterBindingConvention(_isService));
        }
    }
}

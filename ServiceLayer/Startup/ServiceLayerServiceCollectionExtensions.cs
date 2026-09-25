#region licence
// The MIT License (MIT)
// 
// Filename: ServiceLayerServiceCollectionExtensions.cs
// Date Created: 2014/05/20
// 
// Copyright (c) 2014 Jon Smith (www.selectiveanalytics.com & www.thereformedprogrammer.net)
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
#endregion
using System.Reflection;
using DataLayer.DataClasses;
using DataLayer.Startup;
using GenericServices.Configuration;
using GenericServices.Setup;
using Microsoft.Extensions.DependencyInjection;
using ServiceLayer.PostServices;
using StatusGeneric;

namespace ServiceLayer.Startup
{
    /// <summary>
    /// Replaces the old Autofac <c>ServiceLayerModule</c>. Registers GenericServices
    /// (<c>ICrudServices</c> / <c>ICrudServicesAsync</c> + the DTO↔entity AutoMapper config) and the
    /// service-layer's own hand-written services (e.g. <see cref="IPostCrudHelper"/>).
    /// </summary>
    public static class ServiceLayerServiceCollectionExtensions
    {
        /// <summary>
        /// Registers the service layer (and the data layer beneath it). The host must still register the
        /// <see cref="SampleWebAppDb"/> context via <c>AddDbContext&lt;SampleWebAppDb&gt;(...)</c>.
        /// </summary>
        public static IServiceCollection AddServiceLayer(this IServiceCollection services)
        {
            services.AddDataLayer();

            //Runs just before every GenericServices SaveChanges. Reports duplicate Tag.Slug values as
            //validation errors (which controllers copy into ModelState) instead of letting the data layer's
            //ValidationException escape as an unhandled 500. This is the GenericServices replacement for the
            //EF6 ValidateEntity hook.
            var config = new GenericServicesConfig
            {
                BeforeSaveChanges = context =>
                {
                    var status = new StatusGenericHandler();
                    if (context is SampleWebAppDb sampleDb)
                        foreach (var error in sampleDb.GetSlugUniquenessErrors())
                            status.AddError(error);
                    return status;
                }
            };

            //Registers ICrudServices/ICrudServicesAsync and builds the AutoMapper mappings by scanning
            //this assembly for ILinkToEntity<T> DTOs and PerDtoConfig<,> classes.
            services.GenericServicesSimpleSetup<SampleWebAppDb>(config, Assembly.GetExecutingAssembly());

            //Hand-written helper that replaces the DTO's old SetupSecondaryData/Create/Update hooks.
            services.AddScoped<IPostCrudHelper, PostCrudHelper>();

            return services;
        }
    }
}

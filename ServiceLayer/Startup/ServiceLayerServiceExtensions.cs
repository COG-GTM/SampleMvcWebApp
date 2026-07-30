#region licence
// The MIT License (MIT)
// 
// Filename: ServiceLayerServiceExtensions.cs
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
using BizLayer.Startup;
using DataLayer.DataClasses;
using DataLayer.Startup;
using GenericServices.Configuration;
using GenericServices.Setup;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using ServiceLayer.BlogServices;
using ServiceLayer.PostServices;
using StatusGeneric;

namespace ServiceLayer.Startup
{
    /// <summary>
    /// This replaces the Autofac ServiceLayerModule. It registers the service layer and every layer below it.
    /// </summary>
    public static class ServiceLayerServiceExtensions
    {
        public static IServiceCollection AddServiceLayer(this IServiceCollection services, string connectionString,
            bool isAzure = false)
        {
            services.AddDataLayer(connectionString, isAzure);
            services.AddBizLayer();

            //DetailPostDto's blogger/tags handling has no equivalent in EfCore.GenericServices
            services.AddScoped<IPostDtoService, PostDtoService>();
            services.AddScoped<IPostDtoServiceAsync, PostDtoServiceAsync>();

            //this scans this assembly for the ILinkToEntity<T> DTOs and registers ICrudServices/ICrudServicesAsync
            services.GenericServicesSimpleSetup<SampleWebAppDb>(BuildGenericServicesConfig(),
                Assembly.GetAssembly(typeof(BlogListDto)));

            return services;
        }

        /// <summary>
        /// EfCore.GenericServices calls SaveChanges directly, so this hook makes the CRUD services
        /// report the SampleWebAppDb validation errors (data annotations, IValidatableObject and the
        /// Tag Slug uniqueness check) as a status rather than throwing an exception.
        /// </summary>
        public static IGenericServicesConfig BuildGenericServicesConfig()
        {
            return new GenericServicesConfig
            {
                BeforeSaveChanges = ValidateBeforeSaveChanges
            };
        }

        private static IStatusGeneric ValidateBeforeSaveChanges(DbContext context)
        {
            var status = new StatusGenericHandler();
            var db = context as SampleWebAppDb;
            if (db != null)
                status.AddValidationResults(db.ValidateChangedEntities());
            return status;
        }
    }
}

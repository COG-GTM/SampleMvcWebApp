#region licence
// The MIT License (MIT)
// 
// Filename: ServiceLayerModule.cs
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
using System;
using DataLayer.DataClasses;
using GenericServices.Configuration;
using GenericServices.Setup;
using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using ServiceLayer.PostServices;
using StatusGeneric;

namespace ServiceLayer.Startup
{
    public static class ServiceLayerSetup
    {
        private const string SlugIndexName = "IX_Tags_Slug";

        /// <summary>
        /// This registers all items in service layer and below
        /// </summary>
        /// <param name="services"></param>
        /// <param name="connectionString">connection string for the SampleWebAppDb database</param>
        /// <param name="isAzure">true if working with azure database (turns on the retry execution strategy)</param>
        public static IServiceCollection AddServiceLayer(this IServiceCollection services, string connectionString, bool isAzure)
        {
            services.AddDbContext<SampleWebAppDb>(options =>
                options.UseSqlServer(connectionString, sqlOptions =>
                {
                    if (isAzure)
                        sqlOptions.EnableRetryOnFailure();
                }));

            var genericServicesConfig = new GenericServicesConfig
            {
                DtoAccessValidateOnSave = true,
                DirectAccessValidateOnSave = true,
                SaveChangesExceptionHandler = HandleSaveChangesException
            };
            services.GenericServicesSimpleSetup<SampleWebAppDb>(genericServicesConfig, typeof(DetailPostDto).Assembly);
            services.AddSingleton<IGenericServicesConfig>(genericServicesConfig);

            services.AddScoped<IPostService, PostService>();
            services.AddScoped<IPostServiceAsync, PostServiceAsync>();

            return services;
        }

        internal static IStatusGeneric HandleSaveChangesException(Exception exception, DbContext context)
        {
            if (exception is SlugNotUniqueException slugException)
                return new StatusGenericHandler().AddError(slugException.Message, slugException.MemberName);

            if (exception is DbUpdateException { InnerException: SqlException sqlException }
                && (sqlException.Number == 2601 || sqlException.Number == 2627)
                && sqlException.Message.Contains(SlugIndexName))
                return new StatusGenericHandler().AddError("The Slug on this tag must be unique and is already being used.", "Slug");

            return null;
        }
    }
}

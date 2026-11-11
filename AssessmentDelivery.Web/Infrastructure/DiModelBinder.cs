using System;
using System.Web.Mvc;

namespace AssessmentDelivery.Web.Infrastructure
{
    public class DiModelBinder : DefaultModelBinder
    {
        protected override object CreateModel(ControllerContext controllerContext, ModelBindingContext bindingContext, Type modelType)
        {
            return modelType.IsInterface
                       ? DependencyResolver.Current.GetService(modelType)
                       : base.CreateModel(controllerContext, bindingContext, modelType);
        }
    }
}

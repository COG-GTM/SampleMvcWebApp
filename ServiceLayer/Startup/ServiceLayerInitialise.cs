using DataLayer.DataClasses;
using DataLayer.Startup;

namespace ServiceLayer.Startup
{
    /// <summary>
    /// This handles the initialisation of this layer and any other layers
    /// </summary>
    public static class ServiceLayerInitialise
    {
        /// <summary>
        /// This should be called at Startup once the DbContext is available.
        /// </summary>
        /// <param name="context">the application DbContext</param>
        /// <param name="canCreateDatabase">true if the database provider allows the app to create the database</param>
        public static void InitialiseThis(SampleWebAppDb context, bool canCreateDatabase)
        {
            DataLayerInitialise.InitialiseThis(context, canCreateDatabase);
        }
    }
}

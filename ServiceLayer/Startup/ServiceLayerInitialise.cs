using DataLayer.DataClasses;
using DataLayer.Startup;

namespace ServiceLayer.Startup
{
    /// <summary>
    /// This handles the initialisation of this layer and any other layers.
    /// </summary>
    public static class ServiceLayerInitialise
    {
        /// <summary>
        /// This should be called at Startup.
        /// </summary>
        /// <param name="context">the application DbContext</param>
        /// <param name="canCreateDatabase">true if the app may create/migrate the database</param>
        public static void InitialiseThis(SampleWebAppDb context, bool canCreateDatabase)
        {
            //Place any tasks that need initialising here

            DataLayerInitialise.InitialiseThis(context, canCreateDatabase);
        }
    }
}

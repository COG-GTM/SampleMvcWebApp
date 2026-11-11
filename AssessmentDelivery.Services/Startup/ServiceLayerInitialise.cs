using AssessmentDelivery.Business.Startup;
using AssessmentDelivery.Data.Startup;

namespace AssessmentDelivery.Services.Startup
{
    /// <summary>
    /// This handles the initialisation of this layer and any other layers
    /// </summary>
    public static class ServiceLayerInitialise
    {
        /// <summary>
        /// This should be called at Startup
        /// </summary>
        /// <param name="isAzure">true if working with azure database</param>
        public static void InitialiseThis(bool isAzure)
        {
            //Place any tasks that need initialising here
            BizLayerInitialise.InitialiseThis();
            DataLayerInitialise.InitialiseThis(isAzure);
        }
    }
}

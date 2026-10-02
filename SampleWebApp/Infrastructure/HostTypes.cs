namespace SampleWebApp.Infrastructure
{
    public enum HostTypes { NotSet, LocalHost, WebWiz, Azure };

    public static class HostTypesExtensions
    {
        public const string HostTypeConfigKey = "HostType";

        /// <summary>
        /// Decodes the "HostType" configuration value. Returns NotSet if missing or unknown.
        /// </summary>
        public static HostTypes GetHostType(this IConfiguration configuration)
        {
            Enum.TryParse(configuration[HostTypeConfigKey], true, out HostTypes hostType);
            return hostType;
        }
    }
}

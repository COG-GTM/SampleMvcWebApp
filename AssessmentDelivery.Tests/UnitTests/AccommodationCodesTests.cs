using AssessmentDelivery.Business.Rules;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    [TestFixture]
    public class AccommodationCodesTests
    {
        [TestCase("et25", "ET25")]
        [TestCase(" SR ", "SR")]
        [TestCase("", null)]
        public void ParsesKnownCodes(string input, string expected)
        {
            string code;
            Assert.IsTrue(AccommodationCodes.TryParse(input, out code));
            Assert.AreEqual(expected, code);
        }

        [Test]
        public void RejectsUnknownCode()
        {
            string code;
            Assert.IsFalse(AccommodationCodes.TryParse("XX99", out code));
        }

        [Test]
        public void OnlyExtraTimeNeedsAccommodationSeat()
        {
            Assert.IsTrue(AccommodationCodes.NeedsAccommodationSeat("ET50"));
            Assert.IsFalse(AccommodationCodes.NeedsAccommodationSeat("RB"));
        }
    }
}

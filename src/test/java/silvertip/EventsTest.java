package silvertip;

import org.junit.Assert;
import org.junit.Test;

public class EventsTest {

  @Test(expected = IllegalArgumentException.class)
  public void zeroTimeout() throws Exception {
    Events.open(0);
  }
}

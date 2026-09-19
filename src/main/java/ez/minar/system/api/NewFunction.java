package ez.minar.system.api;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NewFunction {
    String name();
    String desc() default "";
    Category category();
}

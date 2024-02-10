package io.github.kensuke1984.kibrary.elastic;

/**
 *  * Enum for elements in modulus
 *   *
 *   * @since 2024/02/11
 *    * @author Ikuchi Funahashi
 *     * @version 0.0.1
 *      */
public enum AnisotropicModulusEnum {
    C11, C12, C13, C14, C15, C16, C22, C23, C24, C25, C26, C33, C34, C35, C36, C44, C45, C46, C55, C56, C66;

    public static AnisotropicModulusEnum getAnisotropic(ElasticMN mn) {
        int m;
        int n;
        if (mn.getN() < mn.getM()) {
            m = mn.getN();
            n = mn.getM();
        } else {
            m = mn.getM();
            n = mn.getN();
        }

        switch (m) {
            case 1:
                switch (n) {
                    case 1:
                        return C11;
                    case 2:
                        return C12;
                    case 3:
                        return C13;
                    case 4:
                        return C14;
                    case 5:
                        return C15;
                    case 6:
                        return C16;
                }
            case 2:
                switch (n) {
                    case 2:
                        return C22;
                    case 3:
                        return C23;
                    case 4:
                        return C24;
                    case 5:
                        return C25;
                    case 6:
                        return C26;
                }
            case 3:
                switch (n) {
                    case 3:
                        return C33;
                    case 4:
                        return C34;
                    case 5:
                        return C35;
                    case 6:
                        return C36;
                }
            case 4:
                switch (n) {
                    case 4:
                        return C44;
                    case 5:
                        return C45;
                    case 6:
                        return C46;
                }
            case 5:
                switch (n) {
                    case 5:
                        return C55;
                    case 6:
                        return C56;
                }
            case 6:
                switch (n) {
                    case 6:
                        return C66;
                }
        }
        throw new RuntimeException("Invalid input");
    }

}
